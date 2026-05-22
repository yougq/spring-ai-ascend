---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-service
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
---

# Architecture Review Proposal: agent-service L1 Domain Expansion (Wave 1.2)

> **Date:** 2026-05-21
> **Author:** LucioIT (Core Architect) & Flash (Agent)
> **Target Wave:** W0/W1 (Immediate Enforcement)
> **Related Rules:** Rule G-1.c (L1 Depth & Grounding), Rule R-G (Reactive I/O), Rule R-M (Engine Extraction)

## 1. Background & Principles

### 1.1 Top-Level Design Context (L0 Architecture)
As a critical part of the overall agent ecosystem, the `agent-service` module is deeply embedded in the **L0 Top-Level Design Architecture**. The L0 architecture is composed of **6 core modules** and **2 core deployment/integration modes**:

#### 1.1.1 Six Core Modules
1. **Agent Client (agent-client)**: Integrated in SaaS applications and desktop applications, responsible for sensing business knowledge and environments, operating business tools, distributing agent configurations, and calling/invoking agent services.
2. **Agent Service (agent-service)**: **(The core domain of this module)** Responsible for packaging the graph-mode execution of workflow agents and the loop-mode execution of ReAct agents into microservices.
3. **Agent Execution Engine (agent-execution-engine)**: Responsible for providing executors for the two major categories of agents, and providing various components for developers, such as nodes used in workflows, and tools and hooks used in ReAct.
4. **Agent Bus (agent-bus)**: Responsible for connecting north-south C/S communication traffic and east-west A2A communication traffic.
5. **Agent Middleware (agent-middleware)**: Responsible for providing basic services required by agents, such as memory services, skill services, knowledge services, sandboxing services, etc.
6. **Agent Evolution Platform (agent-evolve)**: Responsible for online and offline autonomous evolution of agents.

#### 1.1.2 Two Core Deployment/Integration Modes
- **Platform-Centric Mode**: The business side only integrates `agent-client`, and all other modules are deployed on the platform side (centralized hosting and execution, reducing integration overhead on the business side).
- **Business-Centric Mode**: The business side integrates not only `agent-client` but also deploys `agent-service` and `agent-execution-engine` locally (within the physical boundary of the business) to achieve near-computation; the platform side only provides unified governance, interconnection, and basic public services.

### 1.2 Project Phase Context and Evolution Roadmap
To balance "delivery speed" with "long-term vision" during the project incubation phase, this project establishes a clear phased evolution and construction focus:

- **Focus on Ecosystem & Experience, Defer High Concurrency Pressure**: The project is currently in its early stages. Under the premise of ensuring that the long-term vision and critical architecture boundaries are correct, R&D focus should be on **building a healthy agent ecosystem and extreme developer/user experience**, rather than over-investing in solving pure system engineering problems like high concurrency and massive throughput at this stage (the architectural design leaves room for expansion, while the implementation strives to be lightweight and efficient).
- **Phased Construction of Six Modules**:
  - **Prioritize Proprietary Core (Client/Service/Engine)**: In this phase, priority is given to the complete implementation of the three core pillars: `agent-client`, `agent-service`, and `agent-execution-engine`.
  - **Introduce Mature Open-Source (Bus/Middleware)**: For `agent-bus` (bus) and `agent-middleware` (middleware), this phase primarily introduces mature open-source stacks (such as NATS/RabbitMQ/Redis storage/vector databases) and performs lightweight adaptation integration, avoiding closed-door development and fully guaranteeing the delivery speed of the core link.
- **Deployment Mode Evolution Roadmap**:
  - **Current Phase**: Prioritize connecting and fully implementing **Platform-Centric Mode** to quickly run through end-to-end core use cases and achieve business closure.
  - **Next Phase**: Fully deliver support for **Business-Centric Mode**. Although this mode is not delivered in the current phase, **current L1 architecture and SPI designs must deeply consider the isolation and polymorphic call semantics of this mode upfront** to ensure zero code modification on the business side during future transitions.

### 1.3 Design Principles and Core Manifestations
`agent-service` must strictly adhere to the following principles at the L1 level to support core agent manifestations and business evolution needs:

#### 1.3.1 Encapsulation of Two Agent Manifestations
1. **Workflow Agent**: Encapsulates agents executing in graph-mode (Graph), corresponding to highly deterministic directed acyclic graphs (DAGs) or branch processes with complex topologies.
2. **ReAct Agent**: Encapsulates agents executing in loop-mode (Loop), utilizing "reasoning-action" closed loops to autonomously select and invoke tools and hooks to handle non-deterministic tasks.

#### 1.3.2 Two Deployment Forms and Integration Invocation Methods (Dual-Mode)
1. **Embedded Co-process**: `agent-service` and `agent-execution-engine` are co-located in the same process (e.g., same JVM), employing direct method/function-level calls. This targets ultra-low latency and extreme computational performance.
2. **Stateless Service-level**: Runs agents as entirely stateless service nodes in independent execution engines. `agent-service` acts as the management layer, issuing control commands to the execution engine via RPC, gRPC, or A2A bus.

#### 1.3.3 Heterogeneous Agent Compatibility Design Principle
- **Heterogeneous Compatibility & Ecosystem Decoupling**: Supports seamless onboarding of existing, running heterogeneous or legacy agents within customer systems. Through the service-level encapsulation and adapters of `agent-service`, legacy agents are transformed into standard service formats, achieving smooth evolution and unified governance.

#### 1.3.4 Service-Level Backpressure and Statelessness (Reactive & Stateless)
- **Reactive API Design**: The agent service interfaces comprehensively adopt reactive designs. Through backpressure mechanisms, they coordinate system-level traffic upward with the bus/client, and protect the execution engine downward.
- **Dual-Mode Input Traffic Adaptation (Pull & Push)**: The service itself supports both active pulling (Pull) of tasks from the event bus and direct external pushing (Push) of requests (e.g., HTTP/gRPC direct connections), both of which are unified under reactive flow control.
- **Asynchronous Decoupling via Internal Queues**: A high-throughput "event/task queue" is introduced within the microservice boundary. Requests are rapidly published (Publish) upon arrival, and then asynchronously consumed (Consume) by background threads to be dispatched to the execution engine.
- **Statelessness, Caching & Semi-Persistence**:
  - *Under Business-Centric Mode*: The internal event queue can employ highly efficient **in-memory queues** (such as JVM Reactor Sinks) to achieve high-performance, compact deployments.
  - *Under Platform-Centric Mode*: To ensure that the service layer remains completely stateless and supports extreme horizontal elastic downscaling, internal event queues and task states must be connected to external distributed caches or undergo **semi-persistence processing**.

#### 1.3.5 A2A Multi-Agent Collaboration and Symmetric Peer-to-Peer Network
- **Symmetric P2P Invocations**: The `agent-service` layer is no longer a unidirectional API provider; it simultaneously acts as an **A2A Server** and an **A2A Client**. Each agent service not only provides services but must also be capable of invoking other agent services as a client.
- **Full Scenario Collaboration Adaptation**:
  - *Spatial Dimension*: Supports intra-node process-level method invocations, as well as cross-node cross-network A2A distributed invocations.
  - *Lifecycle Dimension*: Supports real-time dynamic splitting and spawning of temporary sub-agents (Dynamic Sub-agents) for temporary collaboration and lifecycle management, as well as flat collaboration with pre-existing, long-lived independent agents.
- **Google `a2a-java` Protocol Stack Integration**: The underlying system uniformly introduces the A2A protocol standard, leveraging Google's official Java implementation, the **`a2a-java`** SDK, to encapsulate end-to-end handshakes, channel establishment, multi-routing management, and session associations, ensuring protocol-level standardization.

#### 1.3.6 Task-Centric Control Plane and A2A Interrupt Signal System
- **Task-Centric Model**: Discard the traditional session-centric synchronous blocking mode, and completely reconstruct the scheduling system around **A2A standard task lifecycle states** as the core, supporting task submission, execution, asynchronous suspension, and final completion.
- **Explicit Stateless Interrupt Primitives**: Eliminate implicit interrupts based on traditional Java exception throwing. When encountering asynchronous or external waiting, the engine must throw explicit, strongly-typed **Interrupt Signals**, triggering dehydration storage and context persistence at the Service layer, thereby fundamentally keeping execution threads highly concurrent and non-blocking.

### 1.4 Logical Execution Granularity and Four-Layer State Lifecycle Boundaries
To precisely define the boundaries of data accumulation and logical execution, this project establishes a four-layer state and lifecycle boundary model from micro to macro:

1. **Layer 1: Run (Transient Single-Step Computation)**: The most micro execution granularity, corresponding to a single node transition in a directed acyclic workflow (Workflow), a single iteration (Loop Turn) in a ReAct autonomous agent thinking loop, or a computational transition between two physical interception hooks. It is a transient state.
2. **Layer 2: Task (Task Control Lifecycle)**: A structured execution entity with a lifecycle and deterministic parameters, derived from message requests of external calling ends (SaaS Client or other collaborative A2A agents). It has a control state chain of Submitted -> Working -> Suspended -> Completed.
3. **Layer 3: Session (Contextual Interaction Data Container)**: A session data container managing multi-round human-machine interactions or peer-to-peer machine collaboration. In simple enterprise scenarios, "Task is Session"; in long-term, multi-task collaboration scenarios, a Session container may contain and concurrently execute multiple Tasks, maintaining the inheritance of multi-round contexts.
4. **Layer 4: Memory (Long-Term Cognitive Asset)**: Long-term knowledge and self-evolving cognitive assets that transcend session interactions and have a lifecycle equal to the physical lifespan of the agent, providing the digital life foundation for self-optimization and evolution.

### 1.5 Core Division of Labor Principles between Service and Engine
Based on the four-layer model, the agent service (Service) and the execution engine (Engine) establish strict division principles at the L1 level:

- **Core Boundary Principle**: **The Task level is the core division interface. Task control belongs to the Service, and Run executions within the Task belong to the Engine.**
- **Engine Boundary (Stateless Pure Computation Chip for Run)**:
  - Focuses on Run-level computation transitions and pure stateless logical loops within a Task.
  - **Core Rule**: The Engine is strictly prohibited from executing any I/O operations (no reading/writing databases, no network A2A address resolution, no direct bus/middleware calls). All inputs required by the Engine must be explicitly and unidirectionally injected by the Service layer as `InjectedContext` prior to execution. When encountering blocking points, the Engine immediately outputs `StateDelta` and `YieldSignal` interrupt primitives, releasing physical execution threads.
- **Service Boundary (Task Control, Session Projection, Memory Accumulation)**:
  - **Task-Level Control**: Service is responsible for maintaining Task-level state machines, executing A2A queue backpressure flow control, and intercepting interrupt signals (InterruptSignal) to perform state dehydration and rehydration.
  - **Session-Level Dynamic Assembly**: Service manages the lifecycle of interaction sessions. Through the `ContextProjector` algorithm, it "semantically projects" the most relevant snippets from the vast history of the Session, assembling them into `InjectedContext` to feed the Engine, completely freeing the engine from state management overhead.
  - **Memory-Level Cognitive Accumulation**: Upon Task completion or suspension, the Service is responsible for synchronizing `StateDelta` to the middleware and evolution platform, extracting long-term memories, accumulating knowledge, and driving agent evolution.

### 1.6 Message-Centric Data Plane and Task-Centric Control Plane Separation
To support the agent's flexible understanding of natural language while guaranteeing determinism in the distributed microservice kernel, the separation of "compile-time and run-time" must be established at the Service interface layer and Engine SPI contracts:

- **Separation of Message and Task**:
  - **Message (Payload)**: Belongs to the **Data Plane**. It is essentially unstructured/semi-structured natural language text representing user intent or interaction facts.
  - **Task (Control Object)**: Belongs to the **Control Plane**. It is a strongly-typed, structured execution entity with a lifecycle and deterministic parameters.
- **Separation of Interface Transformation (Metaphor: Natural Language Compiler)**:
  - **Inbound Decoupling**: When the Service receives a natural language request (Message), it must not pass the Message directly to the engine. Instead, it must wrap and elevate it into a strongly-typed **Task (with historical messages compiled into InjectedContext)** and pass it to the engine.
  - **Outbound Decoupling**:
    - *Natural Language Response*: When the engine completes execution (Completed) and outputs responses, it wraps the natural language in the `newMessages` field of `StateDelta` and passes it back to the Service.
    - *Control Logic Interaction (e.g., A2A Split)*: When the engine reasons that collaboration or suspension (Yield) is required, **it is strictly prohibited from expressing its invocation intent to the service layer via natural language text** (which greatly reduces fragility). The engine must explicitly return strongly-typed **Task interrupt primitives (such as SubTaskAwaitSignal)** in `StateDelta`, leaving network actions like A2A routing, sub-task dispatching, and channel building to be physically executed by the Service (the Agent OS Kernel).

## 2. Scenarios View
The core business scenarios covered by this design are as follows:

### 2.1 High-Performance Co-process Execution Scenario
- **Typical Link**: Business triggers command -> Local `agent-service` loads rapidly -> Drives the co-located `agent-execution-engine` directly via memory/function-level calls -> Passes Delta results via memory and persists them.
- **Application**: Edge computing or Business-Centric deployments where response latency is highly sensitive and resource footprint must be extremely compact.

### 2.2 Legacy Heterogeneous Agent Compatibility Scenario (Service-level)
- **Typical Link**: Business distributes complex decision task -> `agent-service` identifies that the target agent is a legacy/heterogeneous running instance -> Dispatcher switches to service-level mode -> Dispatches via A2A bus or RPC to the external engine instance -> Receives execution state and returns control flow.
- **Application**: Enterprise mixed-deployment scenarios where existing private agents need to be smoothly connected to the unified platform governance framework.

### 2.3 Cross-Node Multi-Agent A2A Asynchronous Collaboration Scenario
- **Typical Link**: Agent A encounters a bottleneck during execution -> Its service layer spins up the A2A client -> Launches cross-node remote calls based on `a2a-java` to deliver a sub-task to Agent B's A2A server -> Agent A dehydrates and suspends -> Agent B completes computation and calls back Agent A's A2A port -> Agent A is rehydrated and resumes context, executing to completion.

## 3. Logical View
The design of core logical components supporting dual-mode invocations:

### 3.1 Polymorphic Dispatcher
- The unified physical entry point for agent invocations. It determines the agent type and execution environment based on registry configurations.
- Provides dual-path polymorphic dispatching via `LocalDirectExecutor` (local JVM direct branch) and `RemoteServiceExecutor` (remote service branch), shielding northern callers from underlying deployment differences.
- **Integrated A2A Routing**: Automatically forwards requests through the integrated A2A client socket for protocol wrapping and cross-node dispatching when the target address is identified as a cross-node or heterogeneous agent.

### 3.2 Engine Adapter
- Abstracts a unified stateless calculation interface, shielding specific execution semantics of Workflow (Graph) and ReAct (Loop) engines.
- Acts as a direct proxy for the `agent-execution-engine` SDK during local co-process runs, and wraps A2A protocol clients or RPC proxies during service-level deployments.

### 3.3 Internal Event Queue
- Located within the microservice boundary, decoupling network I/O threads from CPU-intensive LLM reasoning and execution engine calculation threads.
- **Polymorphic Queue Storage**:
  - *In-Memory Queue*: Built on Project Reactor Sinks / Disruptor within the service boundary, linking directly to in-memory subscription consumption.
  - *Distributed Cache/Semi-Persistent Queue*: Connected to Redis Lists or external lightweight Task Stores, saving active and suspended Task states to guarantee zero task loss and zero computation interruption during platform-centric scale-down and node drift.

### 3.4 A2A Connector
- Integrates Google's **`a2a-java`** SDK within the service layer, containing symmetric peer-to-peer Client/Server components:
  - **A2A Server**: Listens at the northern `api/` layer, receiving peer A2A collaboration requests from other agents (cross-node or intra-process).
  - **A2A Client**: Provides a unified outbound routing socket for the execution engine or orchestrator to dispatch collaboration envelopes to remote agents.

### 3.5 Task-Centric State Control & Signal Dispatcher
- **A2A State Control Component**: Tracks and maintains task transitions across 5 core states in strict compliance with the A2A protocol, exposing unified APIs for monitoring, cancellation, and retries.
- **Interrupt Signal Interceptor**: Intercepts `InterruptSignal` thrown from the execution layer, dynamically resolving the interrupt sub-type (e.g., input waiting, tool waiting, collaboration waiting, policy approval) and dispatching it to specific lifecycle managers.

### 3.6 Logical Boundary Mapping
The logical division of the four-layer lifecycle states and their physical component ownership are defined as:
* **Agent Service (agent-service) Management Layer**:
  - **Memory Layer**: Interfaces with external `agent-middleware` (knowledge, memory) and `agent-evolve`.
  - **Session Layer**: Managed by the service's `SessionManager` and `ContextProjector`, executing semantic projections.
  - **Task Layer (Control Plane)**: Managed by `TaskCenter`, `PolymorphicDispatcher`, and A2A State Control components, executing control flow orchestration, task queuing, and dehydrated storage.
* **Agent Execution Engine (agent-execution-engine) Execution Layer**:
  - **Task Layer (Execution Plane/Engine SPI Contract)**: Accepts structured `TaskSpec` and `InjectedContext` payloads, serving as the stateless computational chip for Tasks (with natural language Message-level Session data shielded).
  - **Run Layer**: Enclosed entirely within the engine's internal implementation, driving workflow node transitions (Node Run) and ReAct thinking iterations (Loop Run).

## 4. Process View
Focuses on task state transitions and non-blocking reactive backpressure flow control:

### 4.1 Asynchronous Task Loop
1. **Task Intake**:
   - Receives Push interface requests (e.g., REST / gRPC) or actively pulls event requests from the bus.
   - `ReactiveOrchestrator` rapidly compiles the request into a standard `Task`, publishes the event to the internal queue, and immediately returns a receipt with `TaskID` to the caller, keeping the physical connection non-blocking.
2. **Backpressured Dispatch**:
   - Background reactive consumer threads (Reactor-based subscriptions) pull tasks on-demand according to backpressure signals `request(N)` and invoke the `Engine Adapter` to begin execution.
3. **Execution & State Dehydration**:
   - The engine returns `StateDelta` and `Yield` signals.
   - *Platform-Centric Mode*: The service layer automatically dehydrates `StateDelta` and execution progress to the shared cache/lightweight database. The current service node is then freed from physical execution threads, maintaining fully stateless characteristics.
   - *Business-Centric Mode*: State updates are written directly to JVM process memory or local lightweight databases.

### 4.2 A2A Collaboration Loop
1. **Spawn/Call**:
   - Agent A spawns a sub-agent or initiates a collaboration request during its execution.
   - The adapter intercepts the command, triggering the A2A client to dispatch an asynchronous request envelope to Agent B's A2A port using `a2a-java`.
2. **Dehydrated Suspend**:
   - Agent A produces a standard `Yield` signal in `agent-service`.
   - The orchestrator dehydrates Agent A's running state, context, and Session data, writing them to the Task Store.
   - Computational processes and threads of Agent A are immediately released back to the pool.
3. **Asynchronous Rehydration & Resume**:
   - Upon completing the computation, Agent B uses its A2A client to return a response envelope to Agent A.
   - Agent A's A2A server captures the callback. The orchestrator rehydrates Agent A's context from the Task Store based on the `TaskID` in the envelope.
   - The task is re-queued in the internal event queue, pulling an execution thread to continue computation.

### 4.3 Four-Layer Life Cycle Flow
1. **Task Conception (Memory -> Session -> Task)**:
   - A task is delivered via A2A or Client. The Service registers the Task in the Task Store and calls `SessionManager` to create/bind a Session.
2. **Context Assembly & Projection (Session -> Task Context)**:
   - Prior to execution dispatch, the service's `ContextProjector` retrieves semantic snippets from multi-round Session history and long-term Memory, assembling them into the `InjectedContext` payload.
3. **Engine Single-Step Loop (Task Context -> Runs Loop)**:
   - The execution engine (Engine) loads the Context, serving as a stateless calculation carrier. It sequentially drives multiple **Runs** (single-step transitions) internally until the task computation is finished or an A2A `Yield` interrupt is triggered.
4. **State Refresh & Memory Accumulation (Run Delta -> Session -> Memory)**:
   - The computation outputs `StateDelta`. The Service intercepts the data, updates and refreshes the Session, and asynchronously triggers background tasks. Through `agent-evolve` and `agent-middleware`, this round's decisions are refined and compiled into long-term Memory, allowing the agent's cognitive capabilities to evolve across sessions.

## 5. Development View
This section strictly complies with **Rule G-1.c (Code Package Mapping Constraint)**, clearly defining boundaries between self-developed structures and dependencies on external **`a2a-java`** SDKs under the agile principle of "never reinvent the wheel."

### 5.1 Boundaries of Open-Source Reuse and Self-Development
We fully integrate Google's official **`a2a-java`** SDK. The following core mechanisms are completely offloaded to the open-source libraries:
1. **Reactive Backpressure Channels**: Uses the built-in reactive connections and Reactor Sinks buffers of `a2a-java`.
2. **Bi-directional C/S Bus Foundation**: Employs peer-to-peer `A2AClient` and `A2AServer` sockets to handle network-level handshake, channel encryption, and multi-routing.
3. **Task-Centric State Control Machine**: Employs standard task lifecycle states and control primitives of `a2a-java`.

Consequently, our **proprietary development focus** is centered on: **local polymorphic routing dispatching, high-performance session context dynamic projection algorithms, microservice northern API gateway integration, and database/Redis adapters for a2a-java SPI**.

### 5.2 Proprietary Code Package Mapping & Dependency Integration
```text
agent-service/src/main/java/com/huawei/ascend/agent/service/
├── api/                        # [Self-Developed + a2a-java Adaptation] Northern API Gateway
│   ├── rest/                   # Northern endpoint for business-side Push mode
│   └── a2a/                    # A2A-Server endpoint, intercepting external collaboration envelopes
├── dispatcher/                 # [100% Proprietary] Local Polymorphic Routing Dispatcher
│   ├── PolymorphicDispatcher.java # Polymorphic routing core, deciding between JVM co-process or A2A network calls
│   └── strategy/               # Decision tree for static and dynamic dispatching rules
├── orchestrator/               # [Deep Integration with a2a-java] Core Task Lifecycle Orchestrator
│   ├── ReactiveOrchestrator.java  # Binds with a2a-java scheduling engine, driving Task event streams
│   └── handler/                # Interceptors for strongly-typed A2A InterruptSignals
├── task/                       # [Partially Self-Developed] Task Control State Store
│   ├── TaskCenter.java         # Management entry point for Task Center
│   └── repository/             # [Proprietary Implementation] Binds Redis/JPA to a2a-java's TaskStore SPI
├── session/                    # [100% Proprietary] Session Data Domain & Dynamic Projection (Core Algorithmic Innovation)
│   ├── SessionManager.java     # Manages lifecycle of multi-round conversational contexts
│   └── projection/             # Semantic projection algorithms, extracting snippets to compile InjectedContext
├── engine/                     # [Proprietary Adaptation] Agent Stateless Calculation Adaptation Layer
│   ├── workflow/               # SDK adapter for Graph-mode (Workflow) execution engine
│   ├── react/                  # SDK adapter for Loop-mode (ReAct) execution engine
│   └── spi/                    # StatelessEngineExecutor SPI contract definition
└── infrastructure/             # [Proprietary Adhesives] Middleware Adaptation & Auto-Configuration
    ├── config/                 # Spring Boot AutoConfiguration for a2a-java stack
    └── persistence/            # Adapters for Redis/NATS, serialization, and network connection pools
```

## 6. Physical View
Deployment topologies of dual-mode integration:

### 6.1 Embedded Co-process Deployment Topology
- `agent-service.jar` and `agent-execution-engine.jar` are packaged within a single process (e.g., a Kubernetes Pod or edge container), sharing physical runtime space. Internal event queues and task states are completely hosted in JVM Heap memory, guaranteeing zero network overhead. Under this topology, A2A calls degrade automatically to high-efficiency in-memory process method invocations.

### 6.2 Decoupled Heterogeneous Microservice Deployment Topology
- `agent-service` is deployed centrally as the master control instance, connecting to independent `agent-execution-engine` clusters or legacy third-party agent instances running on the edge or customer private networks.
- **Multi-Instance Stateless Mode**: Multiple `agent-service` control nodes share an external Redis cache cluster and relational/document databases (Task Store). The internal event queue is offloaded to external middlewares (such as NATS) to allow nodes to scale horizontally and support seamless node drift.
- **A2A Peer Network Topology**: Each `agent-service` instance exposes an A2A Listener port, utilizing `a2a-java` to build a peer-to-peer topological network in a distributed environment to exchange asynchronous collaboration envelopes.

## 7. Appendix: Core SPI Interfaces

### 7.1 A2A Standard Task Lifecycle and Interrupt Type Definitions
```java
package com.huawei.ascend.agent.service.api;

import java.util.Map;

/**
 * A2A Standard Task Lifecycle States
 */
public enum TaskState {
    SUBMITTED,   // Task submitted, queued in internal event queue
    WORKING,     // Execution engine loads context and begins computation
    SUSPENDED,   // Suspended due to interrupt, context physically dehydrated
    COMPLETED,   // Computation completed, outputs StateDelta
    FAILED       // Execution aborted due to exceptions
}

/**
 * A2A Standard Interrupt Primitives
 */
public enum InterruptType {
    INPUT_REQUIRED,   // Human-in-the-Loop interaction waiting (approval or inputs)
    SUB_TASK_AWAIT,   // Sub-agent spawning / External A2A node collaboration waiting
    TOOL_EXECUTION,   // Engine requests Service layer to proxy a physical tool call
    DELAY_AWAIT,      // Time window/delay-based suspension
    POLICY_APPROVAL   // Risk-control suspension (budget, safety, or auditing)
}

/**
 * A2A Strongly-Typed Interrupt Signal
 */
public interface InterruptSignal {
    String getTaskId();
    InterruptType getType();
    Map<String, Object> getPayload();
}
```

### 7.2 StatelessEngineExecutor Engine Contract SPI
This interface serves as the lowest-level stateless core contract between `agent-service` and `agent-execution-engine`. Any engine adapter must implement and comply with this contract:

```java
package com.huawei.ascend.agent.service.engine.spi;

import com.huawei.ascend.agent.service.api.InterruptSignal;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Core Stateless Computational SPI Contract for Agent Engine (Engine SPI Contract)
 * Directly referenced and implemented by the subsequent agent-execution-engine design.
 */
public interface StatelessEngineExecutor {
    /**
     * Stateless execution entry: inputs task specification and projected context, outputs execution StateDelta
     */
    Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
}

/**
 * Structured Task Specification
 */
public class TaskSpec {
    private String taskId;
    private String taskType;                  // WORKFLOW or REACT
    private Map<String, Object> parameters;   // Control parameters
    
    // Getters and Setters...
}

/**
 * Projected Injected Context (100% explicit injection, zero engine autonomous I/O)
 */
public class InjectedContext {
    private String sessionId;
    private List<Message> messageHistory;      // History interactive message list compiled by projection algorithm
    private List<Map<String, Object>> tools;   // Available physical tool definitions for this round
    private Map<String, Object> sessionVars;   // Session temporary contextual variables for this round
    
    // Getters and Setters...
}

/**
 * Message Model in the Data Plane (Standard message payload carried by InjectedContext)
 */
public class Message {
    private String messageId;
    private String role;                      // USER, ASSISTANT, SYSTEM
    private String content;                   // Natural Language Content
    private long timestamp;
    
    // Getters and Setters...
}

/**
 * Computational Output State Delta Package from Engine
 */
public class StateDelta {
    private List<Message> newMessages;         // Computational messages produced in this round (Natural language response)
    private Map<String, Object> updatedVars;   // Changed session variables
    private InterruptSignal interruptSignal;   // strongly-typed interrupt control signal if suspended; null if completed
    
    // Getters and Setters...
}
```
