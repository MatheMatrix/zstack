# ZStack AI Coding Assistant Instructions

## Architecture Overview
ZStack is an open-source IaaS platform for datacenter management via APIs. It uses a **plugin-based architecture** where core orchestration is extensible without impacting existing code. Key frameworks:
- **CloudBus**: Asynchronous messaging system for inter-component communication (see `core/src/main/java/org/zstack/core/cloudbus/`)
- **Workflow Engine**: Manages complex operations with rollback on failure (see `core/src/main/java/org/zstack/core/workflow/`)
- **Cascade Framework**: Propagates operations across dependent resources (see `core/src/main/java/org/zstack/core/cascade/`)
- **Plugin System**: Everything is a plugin; use `PluginRegistry` for extensions (see `core/src/main/java/org/zstack/core/plugin/`)

Major components are organized as Maven modules: `core`, `compute`, `storage`, `network`, `plugin/*`, etc.

## Development Workflow
- **Build**: Use Maven; run `./runMavenProfile premium` for enterprise features or `mvn clean install` for standard build
- **Database**: Deploy schema with `./runMavenProfile deploydb`; uses Hibernate ORM
- **Testing**: Three-tier system - unit tests in modules, integration tests in `test/`, system tests in `testlib/`
- **Debugging**: Simulator module (`simulator/`) mocks hypervisors for local testing
- **Deployment**: WAR file deployment to Tomcat; scripts in `build/` for automation

## Coding Conventions
- **Java 8** with Spring Framework 5.x, Hibernate 5.x
- Packages: `org.zstack.*`; core in `core/`, compute logic in `compute/`
- **Flows for Operations**: VM operations use `Flow` interface with rollback (e.g., `VmStartOnHypervisorFlow.java`)
- **Messages**: Async via CloudBus; extend `Message` for requests, `MessageReply` for responses
- **Extensions**: Use `PluginRegistry.getExtensionList()` for plugin hooks (e.g., `VmBeforeStartOnHypervisorExtensionPoint`)
- **Error Handling**: Use `ErrorCode` and `CloudRuntimeException`; avoid checked exceptions
- **Logging**: `CLogger` from `Utils.getLogger()`
- **Database**: JPA entities with `@Entity`; queries via `DatabaseFacade`

## Key Patterns
- **Plugin Implementation**: Create module under `plugin/`, implement `PluginDriver` interface
- **API Messages**: Extend `APIMessage` for user-facing APIs, handle in managers (e.g., `VmInstanceManagerImpl`)
- **Resource Allocation**: Use workflow chains for multi-step allocations (e.g., host, storage, network)
- **State Machines**: Built-in for resource states; use `Platform.createStateMachine()`
- **Global Config**: Use `GlobalProperty` for runtime configurations

## Integration Points
- **External Services**: RabbitMQ for CloudBus, Ansible for automation
- **Hypervisors**: KVM plugin in `plugin/kvm/`, others like Ceph, NFS
- **Networking**: NFV-based; virtual routers as appliances
- **Storage**: Primary/backup storage abstraction; plugins for different backends

Reference: `README.md` for overview, `pom.xml` for dependencies, `runMavenProfile` for dev scripts.