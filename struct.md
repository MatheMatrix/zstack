graph TD
    User[用户 / UI / CLI]

    %% ==========================================
    %% 1. 管理节点 (Management Node)
    %% ==========================================
    subgraph "ZStack Management Node"
        subgraph "ZStack Core (In-Tree)"
            Core_API[Unified API Entry]
            Scheduler[Scheduler & Resource Allocator]
            Inventory[("Global Resource Inventory<br/>(统一账本: CPU/RAM/BM UUID)")]
            
            %% 统一监控服务端
            Monitor_Server["Unified Monitor Server<br/>(Prometheus/ZWatch)"]

            %% 驱动层
            Driver_KVM[KVM Driver]
            Driver_BM["Bare Metal Driver<br/>(IPMI/PXE/Redfish)"]

            %% 容器扩展接口
            Interface_Def["Container Driver Interface<br/>(SPI)"]
        end

        subgraph "Container Module (Out-of-Tree)"
            C_Adapter[Container Adapter]
            C_Logic[Container Business Logic]
        end
    end

    %% ==========================================
    %% 2. 物理基础设施 (Physical Infrastructure)
    %% ==========================================
    subgraph "Physical Infrastructure"
        
        %% --- 类型 A: 虚拟化/容器宿主机 (资源共享) ---
        subgraph "Type A: Hypervisor Node (Shared Resource)"
            direction TB
            Host_OS["Host Linux Kernel"]
            
            subgraph "Host Agents (Shared Host)"
                Agent_Core["KVM Agent (Python)"]
                Agent_Container["Container Agent"]
                
                %% 复用的监控库 (部署在宿主机)
                Lib_Mon_Host[("Monitor Lib (Python)<br/>Reused")]
            end

            subgraph "Virtual Runtimes"
                Runtime_Libvirt[Libvirt / QEMU]
                Runtime_Docker[Container Runtime]
            end

            subgraph "Shared Instances"
                Instance_VM((VM Instance))
                Instance_Container((Container))
            end
        end

        %% --- 类型 B: 裸金属节点 (资源独占) ---
        subgraph "Type B: Bare Metal Node (Exclusive Resource)"
            direction TB
            BM_Hardware["Physical Hardware"]
            
            subgraph "BM Management"
                %% BM Agent 通常运行在带外或辅助OS中，但逻辑上属于该节点管理
                Agent_BM["Bare Metal Agent<br/>(Python)"]
                
                %% 复用的监控库 (用于裸金属)
                Lib_Mon_BM[("Monitor Lib (Python)<br/>Reused")]
            end

            subgraph "Exclusive Instance"
                Instance_BM_OS((User OS / App<br/>Direct Hardware Access))
            end
        end
    end

    %% ==========================================
    %% 连线逻辑
    %% ==========================================

    %% 1. 请求与调度
    User --> Core_API
    Core_API --> Scheduler
    Scheduler -- "Check Inventory" --> Inventory
    Core_API -. "Query Metrics" .- Monitor_Server

    %% 2. 路径分发
    Scheduler -- "Path: VM" --> Driver_KVM
    Scheduler -- "Path: Container" --> Interface_Def
    Scheduler -- "Path: Bare Metal" --> Driver_BM

    %% 3. 驱动控制
    Driver_KVM -- "Http Cmd" --> Agent_Core
    Interface_Def -.-> C_Adapter
    C_Adapter --> C_Logic
    C_Logic -- "Http Cmd" --> Agent_Container
    
    %% 裸金属控制 (通常通过带外网络或辅助网关)
    Driver_BM -- "IPMI/Http" --> Agent_BM

    %% 4. 宿主机内部逻辑 (Type A)
    Agent_Core --> Runtime_Libvirt
    Runtime_Libvirt --> Instance_VM
    
    Agent_Container --> Runtime_Docker
    Runtime_Docker --> Instance_Container

    %% 监控复用 (Type A)
    Agent_Core -- "Call" --> Lib_Mon_Host
    Lib_Mon_Host -- "Push Metrics" --> Monitor_Server

    %% 5. 裸金属内部逻辑 (Type B)
    Agent_BM -- "Power/Boot Control" --> BM_Hardware
    BM_Hardware === Instance_BM_OS
    
    %% 监控复用 (Type B)
    Agent_BM -- "Call" --> Lib_Mon_BM
    Lib_Mon_BM -- "Push Metrics" --> Monitor_Server

    %% 6. 资源回报
    Agent_Core -- "Sync Capacity" --> Inventory
    Agent_BM -- "Sync State (Occupied)" --> Inventory

    %% ==========================================
    %% 样式定义
    %% ==========================================
    classDef core fill:#e3f2fd,stroke:#1565c0;
    classDef ext fill:#fff3e0,stroke:#ff9800,stroke-dasharray: 5 5;
    classDef infra_shared fill:#f5f5f5,stroke:#666;
    classDef infra_bm fill:#e0e0e0,stroke:#333,stroke-width:2px;
    classDef storage fill:#fff9c4,stroke:#fbc02d,stroke-width:2px;
    classDef monitor fill:#c8e6c9,stroke:#2e7d32;

    class Core_API,Scheduler,Driver_KVM,Driver_BM,Interface_Def core;
    class C_Adapter,C_Logic ext;
    class Inventory storage;
    class Monitor_Server,Lib_Mon_Host,Lib_Mon_BM monitor;
    
    %% 区分宿主机和裸金属的样式
    class Host_OS,Agent_Core,Agent_Container,Runtime_Libvirt,Runtime_Docker infra_shared;
    class BM_Hardware,Agent_BM,Instance_BM_OS infra_bm;