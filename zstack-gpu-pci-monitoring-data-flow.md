# ZStack GPU PCI地址映射监控数据流流程图

## 完整数据流架构图

```mermaid
graph TB
    subgraph "数据收集层"
        A1[VM Agent] --> A2[收集GPU指标]
        A2 --> A3[附加VM PCI地址标签]
        A3 --> A4[HTTP POST /zwatch/metrics]
    end

    subgraph "API处理层"
        B1[APIPutMetricDataMsg] --> B2[ZWatchManagerImpl.handle]
        B2 --> B3[CustomNamespace.write]
    end

    subgraph "数据增强层"
        C1[CustomNamespace.write] --> C2[添加账户标签<br/>AccountUUID]
        C2 --> C3[添加实例标签<br/>zstack_instance]
        C3 --> C4[调用DatabaseDriver.write]
    end

    subgraph "存储驱动层"
        D1[PrometheusDatabaseDriver.write] --> D2[转换PushGateway.Data]
        D2 --> D3[生成序列名称<br/>namespace::metricName]
        D3 --> D4[PushGateway.push<br/>推送到Prometheus]
    end

    subgraph "Prometheus存储"
        E1[PushGateway] --> E2[接收指标数据]
        E2 --> E3[存储到TSDB]
        E3 --> E4[应用RecordingRule]
    end

    subgraph "查询处理层"
        F1[ZQL查询/API查询] --> F2[APIGetMetricDataMsg]
        F2 --> F3[ZWatchManagerImpl.handle]
        F3 --> F4[VmNamespace.query]
    end

    subgraph "动态标签增强"
        G1[VmNamespace.query] --> G2[检查GPU指标]
        G2 --> G3{是GPU指标?}
        G3 -->|是| G4[addHostPciAddressLabels]
        G3 -->|否| G5[直接返回数据]
        G4 --> G6[收集缓存键<br/>vmUuid:vmPciAddress]
        G6 --> G7[批量查询映射<br/>VmGpuPciMappingService]
        G7 --> G8[添加Host PCI标签<br/>PciDeviceAddressOnHost]
        G8 --> G9[返回增强数据]
    end

    subgraph "缓存优化层"
        H1[VmGpuPciMappingService] --> H2[ConcurrentHashMap缓存]
        H2 --> H3{TTL过期检查<br/>5分钟}
        H3 -->|未过期| H4[返回缓存结果<br/>~0.1ms]
        H3 -->|已过期| H5[批量数据库查询<br/>~5-10ms]
        H5 --> H6[更新缓存]
        H6 --> H7[返回查询结果]
    end

    A4 --> B1
    B3 --> C1
    C4 --> D1
    D4 --> E1
    F4 --> G1
    G4 --> H1

    style A1 fill:#e1f5fe
    style B1 fill:#f3e5f5
    style C1 fill:#e8f5e8
    style D1 fill:#fff3e0
    style E1 fill:#fce4ec
    style F1 fill:#f3e5f5
    style G1 fill:#e8f5e8
    style H1 fill:#e0f2f1
```

## 核心组件交互时序图

```mermaid
sequenceDiagram
    participant Agent as VM Agent
    participant API as APIPutMetricDataMsg
    participant Mgr as ZWatchManagerImpl
    participant NS as CustomNamespace
    participant Driver as PrometheusDatabaseDriver
    participant PG as PushGateway
    participant Prom as Prometheus

    Agent->>API: HTTP POST /zwatch/metrics<br/>数据: MetricDatum[]
    API->>Mgr: handle(APIPutMetricDataMsg)
    Mgr->>NS: write(namespace, data, accountUuid)
    NS->>NS: 添加标签(AccountUUID, zstack_instance)
    NS->>Driver: write(namespaceName, data)
    Driver->>Driver: 转换PushGateway.Data<br/>生成序列名 namespace::metricName
    Driver->>PG: push(dps)
    PG->>Prom: 推送指标数据
    Prom->>Prom: 存储到TSDB

    Note over Agent,Prom: 数据收集与存储流程
```

```mermaid
sequenceDiagram
    participant Query as ZQL/API Query
    participant API as APIGetMetricDataMsg
    participant Mgr as ZWatchManagerImpl
    participant NS as VmNamespace
    participant Service as VmGpuPciMappingService
    participant DB as Database

    Query->>API: 查询GPU指标
    API->>Mgr: handle(APIGetMetricDataMsg)
    Mgr->>NS: query(MetricQueryObject)
    NS->>NS: 检查是否GPU指标
    alt 是GPU指标
        NS->>NS: collectCacheKeys(data)
        NS->>Service: getHostPciAddressesBatch(cacheKeys)
        Service->>Service: 检查缓存
        alt 缓存命中
            Service->>NS: 返回缓存结果 (~0.1ms)
        else 缓存未命中
            Service->>DB: 批量查询映射关系 (~5-10ms)
            DB->>Service: 返回映射数据
            Service->>Service: 更新缓存
            Service->>NS: 返回查询结果
        end
        NS->>NS: addHostPciAddressLabels(data)
    end
    NS->>Query: 返回增强数据

    Note over Query,DB: 数据查询与标签增强流程
```

## 关键数据转换图

```mermaid
graph LR
    subgraph "Agent原始数据"
        A1[MetricDatum<br/>metricName: gpu_utilization<br/>value: 45.5<br/>labels: {vm_uuid, host_uuid}<br/>time: timestamp]
    end

    subgraph "API消息数据"
        B1[APIPutMetricDataMsg<br/>namespace: "vm"<br/>data: List<MetricDatum>]
    end

    subgraph "Namespace增强数据"
        C1[MetricDatum<br/>metricName: gpu_utilization<br/>value: 45.5<br/>labels: {<br/>  vm_uuid, host_uuid,<br/>  AccountUUID: "acc-xxx",<br/>  zstack_instance: "Custom"<br/>}<br/>time: timestamp]
    end

    subgraph "PushGateway数据"
        D1[PushGateway.Data<br/>metricName: "vm::gpu_utilization"<br/>value: 45.5<br/>labels: {vm_uuid, host_uuid, AccountUUID, zstack_instance}<br/>time: timestamp]
    end

    subgraph "Prometheus存储"
        E1[时序数据<br/>__name__: "vm::gpu_utilization"<br/>vm_uuid: "vm-123"<br/>host_uuid: "host-456"<br/>AccountUUID: "acc-xxx"<br/>zstack_instance: "Custom"]
    end

    subgraph "查询时标签增强"
        F1[Datapoint<br/>labels: {<br/>  vm_uuid, host_uuid,<br/>  AccountUUID, zstack_instance,<br/>  PciDeviceAddressOnHost: "0000:01:00.0"<br/>}<br/>value: 45.5<br/>time: timestamp]
    end

    A1 --> B1
    B1 --> C1
    C1 --> D1
    D1 --> E1
    E1 --> F1

    style A1 fill:#e1f5fe
    style B1 fill:#f3e5f5
    style C1 fill:#e8f5e8
    style D1 fill:#fff3e0
    style E1 fill:#fce4ec
    style F1 fill:#e0f2f1
```

## 性能优化流程图

```mermaid
graph TD
    subgraph "缓存策略"
        A1[查询请求] --> A2[收集缓存键<br/>vmUuid:vmPciAddress]
        A2 --> A3[批量查询映射<br/>getHostPciAddressesBatch]
    end

    subgraph "缓存检查"
        B1[ConcurrentHashMap<br/>mappingCache] --> B2{缓存存在?}
        B2 -->|是| B3{TTL过期?<br/>5分钟}
        B2 -->|否| B4[缓存未命中]
        B3 -->|否| B5[返回缓存结果<br/>~0.1ms]
        B3 -->|是| B4
    end

    subgraph "数据库查询"
        C1[批量数据库查询<br/>IN查询优化] --> C2[返回映射结果<br/>~5-10ms]
        C2 --> C3[更新缓存<br/>mappingCache + cacheTimestamp]
    end

    subgraph "结果返回"
        D1[组装Host PCI地址] --> D2[添加到数据点标签] --> D3[返回增强数据]
    end

    A3 --> B1
    B4 --> C1
    B5 --> D1
    C3 --> D1

    style A1 fill:#e1f5fe
    style B1 fill:#fff3e0
    style C1 fill:#fce4ec
    style D1 fill:#e8f5e8
```

## ZQL查询支持流程图

```mermaid
graph TD
    subgraph "ZQL查询语法"
        A1[ZQL查询] --> A2["query metric from ZStack/VM::GpuUtilization<br/>where pciDeviceAddressOnHost = '0000:01:00.0'"]
    end

    subgraph "查询解析"
        B1[ZQL解析器] --> B2[提取查询条件<br/>namespace: ZStack/VM<br/>metricName: GpuUtilization<br/>labels: pciDeviceAddressOnHost='0000:01:00.0']
    end

    subgraph "数据查询"
        C1[GetMetricDataFunc] --> C2[调用Namespace.query<br/>MetricQueryObject]
        C2 --> C3[VmNamespace.query<br/>动态标签增强]
        C3 --> C4[addHostPciAddressLabels]
        C4 --> C5[标签匹配过滤<br/>pciDeviceAddressOnHost = '0000:01:00.0']
    end

    subgraph "结果返回"
        D1[过滤后的数据点] --> D2[包含Host PCI地址标签] --> D3[ZQL查询结果]
    end

    A2 --> B1
    B2 --> C1
    C5 --> D1

    style A1 fill:#e1f5fe
    style B1 fill:#f3e5f5
    style C1 fill:#e8f5e8
    style D1 fill:#fce4ec
```

## 组件依赖关系图

```mermaid
graph TD
    subgraph "核心组件"
        A1[ZWatchManagerImpl] --> A2[CustomNamespace]
        A2 --> A3[PrometheusDatabaseDriver]
        A3 --> A4[PushGateway]
        A4 --> A5[Prometheus]
    end

    subgraph "查询组件"
        B1[VmNamespace] --> B2[VmGpuPciMappingService]
        B2 --> B3[DatabaseFacade]
        B2 --> B4[ConcurrentHashMap<br/>缓存]
    end

    subgraph "数据对象"
        C1[MetricDatum] --> C2[PushGateway.Data]
        C2 --> C3[Prometheus时序数据]
        C3 --> C4[Datapoint<br/>查询结果]
    end

    subgraph "配置组件"
        D1[VmPrometheusNamespace] --> D2[RecordingRule]
        D2 --> D3[Prometheus规则]
    end

    A1 --> B1
    B1 --> C1
    A3 --> D1

    style A1 fill:#e1f5fe
    style B1 fill:#f3e5f5
    style C1 fill:#e8f5e8
    style D1 fill:#fff3e0
```

## 总结

### 数据流关键节点
1. **Agent收集** → **API接收** → **Namespace增强** → **Driver存储** → **Prometheus持久化**
2. **查询请求** → **Namespace查询** → **动态标签增强** → **缓存优化** → **结果返回**

### 性能优化点
- **批量查询**：N个数据点1次DB查询 vs N次查询
- **内存缓存**：~0.1ms响应 vs ~5-10ms DB查询
- **TTL过期**：5分钟自动清理，防止内存泄漏

### 扩展性设计
- **插件化架构**：DatabaseDriver接口支持不同存储后端
- **动态标签**：查询时实时增强，支持灵活的标签组合
- **缓存抽象**：ConcurrentHashMap + TTL，支持高并发场景

### 监控指标
- 缓存命中率：目标 > 95%
- 查询延迟：缓存命中 < 1ms，DB查询 < 10ms
- 内存占用：映射关系缓存 < 10MB（取决于VM/GPU数量）</content>
<parameter name="filePath">/Users/jinjin/dev/zstack-2/zstack/zstack-gpu-pci-monitoring-data-flow.md