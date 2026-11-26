import assert from 'node:assert/strict';
import test from 'node:test';
import { pendingQuestion, readChatStream } from '../../main/resources/static/chat/chat-stream.mjs';

const event = (eventType, fields = {}) => ({ schemaVersion: '1.0', eventType, ...fields });
const frame = (value, eol = '\n') => `data: ${JSON.stringify(value)}${eol}${eol}`;
const response = (text, contentType = 'text/event-stream; charset=UTF-8') => new Response(new ReadableStream({
  start(controller) {
    for (const byte of new TextEncoder().encode(text)) controller.enqueue(Uint8Array.of(byte));
    controller.close();
  }
}), { headers: { 'Content-Type': contentType } });

test('reads LF, CRLF, and CR frames, multiple data lines, and split UTF-8', async () => {
  const text = ': heartbeat\r\n\r\ndata: {"schemaVersion":"1.0",\r\ndata: "eventType":"content","content":"café"}\r\n\r\n'
    + frame(event('content', { content: ' ☕' }), '\r') + frame(event('content', { content: '!' }));
  const parts = [];
  assert.deepEqual(await readChatStream(response(text), value => parts.push(value.content)), { error: '', pending: null });
  assert.equal(parts.join(''), 'café ☕!');
});

test('a completion or later content does not erase a server error', async () => {
  const text = frame(event('error', { message: 'Search failed' })) + frame(event('complete')) + frame(event('content'));
  assert.deepEqual(await readChatStream(response(text), () => {}), { error: 'Search failed', pending: null });
});

test('recognizes a human-loop pause after the stream closes', async () => {
  const prompt = { status: 'AWAITING_INPUT', question: 'Approve?', options: ['Yes', 'No', null] };
  const expected = { question: 'Approve?', options: ['Yes', 'No'] };
  const text = frame(event('content', { stage: 'human_loop', content: JSON.stringify(prompt) }))
    + frame(event('complete', { stage: 'human_loop' }));
  const prompts = [];
  const outcome = await readChatStream(response(text), (_, approval) => { if (approval) prompts.push(approval); });
  assert.deepEqual(prompts, [expected]);
  assert.deepEqual(outcome, { error: '', pending: expected });
  assert.deepEqual(pendingQuestion([prompt]), expected);
});

test('rejects invalid media, empty, malformed, and incomplete responses', async () => {
  const read = input => readChatStream(input, () => {});
  await assert.rejects(read(response('{}', 'application/json')), /Expected a server-sent event stream/);
  await assert.rejects(read(new Response(null, { status: 403 })), /403/);
  await assert.rejects(read(response(': heartbeat\n\n')), /No chat events/);
  await assert.rejects(read(response('data: {bad}\n\n')), /invalid JSON/);
  await assert.rejects(read(response('data: {}\n\n')), /unsupported chat stream/);
  await assert.rejects(read(response(frame(event('content')) + 'data: {')), /middle of an event/);
});