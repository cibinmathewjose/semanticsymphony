export function pendingQuestion(value) {
  const prompt = Array.isArray(value) ? value[0] : value;
  if (prompt?.status !== 'AWAITING_INPUT' || typeof prompt.question !== 'string') return null;
  const options = Array.isArray(prompt.options)
    ? prompt.options.filter(option => typeof option === 'string' && option.trim()) : [];
  return { question: prompt.question, options };
}

export async function readChatStream(response, onEvent) {
  if (!response.ok) throw new Error('Request failed with status ' + response.status);
  const mediaType = response.headers.get('content-type')?.split(';')[0].trim().toLowerCase();
  if (mediaType !== 'text/event-stream' || !response.body) {
    throw new Error('Expected a server-sent event stream.');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let line = '';
  let skipLf = false;
  let data = [];
  let sawEvent = false;
  let error = '';
  let pending = null;

  const consume = text => {
    for (const char of text) {
      if (skipLf) {
        skipLf = false;
        if (char === '\n') continue;
      }
      if (char !== '\r' && char !== '\n') {
        line += char;
        continue;
      }
      skipLf = char === '\r';
      if (line === '' && data.length) {
        let event;
        try {
          event = JSON.parse(data.join('\n'));
        } catch {
          throw new Error('Received an invalid JSON stream event.');
        }
        data = [];
        if (event?.schemaVersion !== '1.0' || typeof event.eventType !== 'string') {
          throw new Error('Received an unsupported chat stream event.');
        }
        sawEvent = true;
        let approval = null;
        if (event.eventType === 'error') error ||= event.message || 'The server reported a streaming error.';
        if (event.eventType === 'content' && event.stage === 'human_loop') {
          approval = pendingQuestion(JSON.parse(event.content));
          if (approval) pending = approval;
        }
        onEvent(event, approval);
      } else if (line === 'data' || line.startsWith('data:')) {
        data.push(line.slice(5).replace(/^ /, ''));
      }
      line = '';
    }
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      consume(decoder.decode(value, { stream: true }));
    }
    consume(decoder.decode());
    if (error) return { error, pending: null };
    if (data.length || line === 'data' || line.startsWith('data:')) {
      throw new Error('The chat stream ended in the middle of an event.');
    }
    if (!sawEvent) throw new Error('No chat events were received.');
    return { error: '', pending };
  } finally {
    // Cancel an unfinished stream without masking the error that caused us to stop.
    reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}