# Symphony AI Standalone Platform Architecture

## Direction

Symphony AI will evolve from a Java/Spring Boot framework into a standalone, deployable enterprise Agentic AI platform. Java remains the primary backend implementation language, but the product will expose stable APIs and SDKs so applications do not need to embed the framework.

The platform will provide a web console for workflow design, agent management, model configuration, execution, evaluation, observability, and governance.

## Product principles
1. Standalone runtime: workflows and agents execute in Symphony-managed runtime services, not inside a customer's application process.
2. API-first: every console capability is backed by versioned APIs. Provide Java SDK support first; keep the API usable by other languages.
3. Workflow-first: workflows are versioned, validated, testable artifacts with explicit inputs, outputs, policies, and execution history.
4. Model-agnostic: providers are adapters behind a common model gateway; workflows refer to logical model profiles rather than provider-specific classes.
5. Secure by default: tenant isolation, identity propagation, least-privilege tool permissions, secret references, audit events, and policy enforcement are core runtime concerns.
6. Durable execution: long-running workflows, retries, human approvals, timers, and recovery survive process restarts.
7. Observable and evaluable: traces, step events, token/cost usage, latency, outcomes, and evaluation results are first-class data.
8. Incremental evolution: preserve existing Java APIs where practical and introduce compatibility adapters for breaking changes.

## Proposed logical architecture
- Web console: workflow designer, agent studio, model registry, evaluation workbench, execution explorer, administration.
- API and identity layer: REST APIs, authentication, authorization, tenant resolution, API keys/OIDC integration, rate limits.
- Control plane: tenant and project management, workflow/agent versioning, model and tool configuration, policy management.
- Execution plane: workflow scheduler, durable orchestration, agent runtime, bounded worker pools, event streaming, cancellation and retry.
- AI services: model gateway, prompt/context management, memory, RAG/retrieval, evaluation.
- Tool integration: typed tool registry, MCP adapters, A2A adapters, REST/SQL/GraphQL connectors, plugin isolation.
- Data plane: relational metadata store, checkpoint/event store, object storage for artifacts, optional vector store, cache.
- Governance and observability: policy decision/enforcement, approval tasks, immutable audit trail, OpenTelemetry traces/metrics/logs.

## Deployment recommendation

Start with a modular monolith control plane plus independently scalable execution workers, not dozens of microservices. Keep module boundaries and contracts explicit so components can be extracted when scale, isolation, or independent release cadence justifies it.

A practical initial deployment consists of:
- symphony-server: API, control plane, identity integration, administration.
- symphony-worker: workflow and agent execution.
- symphony-web: standalone browser-based console.
- PostgreSQL (or another supported relational database) for metadata and durable workflow state.
- Redis only for cache/ephemeral coordination where required; it must not be the sole durable checkpoint store.
- Object storage for documents, artifacts, and large execution payloads.
- OpenTelemetry-compatible telemetry pipeline.

## Core domain model

Tenant -> Project -> Workflow/Agent -> Version -> Execution -> Step/Tool invocation.

Every persisted record and every runtime operation must carry a trusted tenant and project scope. Tenant identifiers supplied by end users must never be trusted without authorization checks.

## Recommended implementation sequence

### 1. Foundation and safety
- Define module boundaries and public API contracts.
- Add tenant-aware identity and authorization context.
- Replace unrestricted dynamic plugin loading with an allowlisted, typed tool registry.
- Validate workflow definitions before execution.
- Define execution, cancellation, timeout, retry, and error contracts.
- Add security tests for cross-tenant access and tool authorization.

### 2. Durable execution
- Persist workflow definitions and immutable versions.
- Persist execution state and step transitions transactionally.
- Implement idempotency keys, retry policies, leases, recovery, and worker heartbeats.
- Make human approvals durable, authenticated, expiring, and auditable.
- Test restart and multi-worker recovery scenarios.

### 3. AI platform capabilities
- Introduce a provider-neutral model gateway and logical model profiles.
- Standardize structured output and tool-call contracts.
- Add context budgeting, memory interfaces, retrieval and reranking.
- Add model fallback/routing with explicit policy and cost limits.

### 4. Product experience
- Deliver API-first workflow and agent management.
- Build visual workflow designer and execution explorer.
- Add model comparison, evaluation datasets, regression runs, and release gates.
- Add governance administration and audit views.

### 5. Scale and ecosystem
- Add additional storage and model adapters.
- Extract services only where measured scaling or isolation requirements justify it.
- Publish Java SDK, API specifications, extension SDK, deployment charts, and upgrade guides.

## Architectural decisions to validate
- Supported relational databases and migration strategy.
- OIDC provider and organization/tenant mapping.
- Whether workflow execution uses an internal durable scheduler or an optional external orchestration backend.
- Supported queue/event transport for distributed workers.
- Data residency, retention, encryption, and customer-managed key requirements.
- Plugin isolation model (in-process restricted plugins versus out-of-process containers).

## Non-goals for the first release
- Splitting every module into a microservice.
- Supporting every model provider and database simultaneously.
- Treating an LLM-generated plan as trusted executable policy.
- Claiming production readiness before security, durability, load, and recovery tests pass.

## Acceptance criteria for a production foundation
- A workflow resumes correctly after server and worker restarts.
- Two tenants cannot read, mutate, or execute each other's resources.
- Tool invocation is authorized at execution time, not only at design time.
- Duplicate delivery does not duplicate non-idempotent side effects.
- Every execution has correlated audit and telemetry records.
- Workflow versions are immutable and reproducible with recorded model/tool configuration.
- CI runs unit, integration, security, and compatibility tests.
