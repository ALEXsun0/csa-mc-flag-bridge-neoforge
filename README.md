# CSA Minecraft Flag Bridge

这是一个用于 Ret2Shell + 共享 Minecraft 服务器的 NeoForge 1.21.1 动态 flag bridge。Ret2Shell 为每个队伍/用户生成 `FLAG` 和 `CSA_TOKEN`，target 容器启动后把 `token -> flag` 注册到 Minecraft 服务端 mod；玩家在 Minecraft 内执行 `/csa bind <token>` 后，服务端 mod 可以在题目条件达成时把对应 flag 发回 target 容器页面或发给玩家。

仓库里不包含任何真实部署 IP、注册密钥、checker 密钥或比赛 bucket 配置。`ret2shell/` 目录只提供可复制的模板。

## 模块划分

- `server`：只放在 Minecraft 服务端。负责 HTTP 注册接口、`/csa` 命令、未绑定玩家限制、token 绑定状态、flag 发放和 claim callback。
- `terminal`：内容模组，只注册 `csa_flag_bridge:flag_terminal` 方块和物品。可以放入客户端包；不包含 Ret2Shell 地址、token、secret 或部署配置。
- `aero`：航空学内容模组，只注册 `csa_aero_bridge:flight_recorder` 方块和物品。客户端和服务端都需要放；不包含 Ret2Shell 配置。
- `aero-server`：只放在 Minecraft 服务端。负责 `/csaero recorder`、多路线飞行认证，以及通过 `server` 模组按放置者 UUID 发放 flag。

不要把 `server` 或 `aero-server` 模组放入客户端整合包。

## 构建

要求：

- JDK 21
- Gradle 8.x 或 9.x

```bash
gradle :server:build :terminal:build :aero:build :aero-server:build
```

构建产物：

```text
server/build/libs/csa_flag_bridge_server-<version>.jar
terminal/build/libs/csa_flag_terminal-<version>.jar
aero/build/libs/csa_aero_bridge-<version>.jar
aero-server/build/libs/csa_aero_bridge_server-<version>.jar
```

## Minecraft 服务端配置

第一次启动 `server` 模组后会生成：

```text
config/csa_flag_bridge/config.json
```

示例配置：

```json
{
  "enableHttpServer": true,
  "httpHost": "0.0.0.0",
  "httpPort": 18080,
  "allowedRegistrationSourceCidrs": [
    "10.233.64.0/18"
  ],
  "registrationSecret": "replace-with-random-secret",
  "maxPlayersPerToken": 5,
  "allowTokenRebind": false,
  "claimOncePerPlayer": true,
  "consumeTokenOnFirstClaim": false,
  "enableClaimCallback": true,
  "claimCallbackTimeoutMillis": 2500,
  "bindGateEnabled": true,
  "bindGateBypassOps": true,
  "messagePrefix": "[CSA]"
}
```

关键字段：

- `httpHost`：如果 Ret2Shell target pod 需要访问该接口，通常监听 `0.0.0.0`。
- `httpPort`：Ret2Shell target 注册 token 的端口。
- `allowedRegistrationSourceCidrs`：只允许 Ret2Shell pod CIDR 或可信节点访问注册接口。留空表示不限制来源。
- `registrationSecret`：注册接口密钥。不要提交真实值；需要与 Ret2Shell checker/target 环境变量一致。
- `maxPlayersPerToken`：同一个 token 允许绑定的 Minecraft 玩家数量，适合多人合作。
- `claimOncePerPlayer`：普通玩家是否只能领取一次。OP/管理员会绕过此限制。
- `consumeTokenOnFirstClaim`：首次领取后是否消费 token。OP/管理员不会消费 token。
- `enableClaimCallback`：开启后，若注册时提供 `callback_url` 和 `callback_secret`，flag 会回传到 target 容器页面，方便选手复制。

## 航空学附属模组

安装边界：

- 客户端包：放 `csa_aero_bridge-<version>.jar`。
- 服务端：放 `csa_aero_bridge-<version>.jar` 和 `csa_aero_bridge_server-<version>.jar`。
- 服务端仍然需要 `csa_flag_bridge_server-<version>.jar`，客户端不要放服务端 jar。

第一次启动 `aero-server` 后会生成：

```text
config/csa_aero_bridge/config.json
```

示例配置：

```json
{
  "recorderCooldownSeconds": 5,
  "maxRecordersInInventory": 8,
  "stableCruiseTicks": 60,
  "minHorizontalSpeed": 1.0,
  "maxVerticalSpeed": 4.0,
  "maxAngularSpeed": 1.5,
  "maxHorizontalSpeedJitter": 8.0,
  "highSpeedClaimEnabled": true,
  "highSpeedTicks": 40,
  "highSpeedHorizontalSpeed": 8.0,
  "altitudeClaimEnabled": true,
  "altitudeTicks": 40,
  "altitudeY": 180.0,
  "altitudeMinHorizontalSpeed": 0.0,
  "recorderTickInterval": 5,
  "recorderHudIntervalTicks": 5,
  "starterHoneyGlueEnabled": true,
  "starterHoneyGlueItemId": "simulated:honey_glue",
  "starterHoneyGlueCount": 8,
  "starterGogglesEnabled": true,
  "starterGogglesItemId": "create:goggles",
  "starterGogglesCount": 1,
  "sheepProductionBoostEnabled": true,
  "sheepShearingWoolCount": 16,
  "sheepDeathWoolDropMultiplier": 8,
  "messagePrefix": "[CSA Aero]"
}
```

玩家流程：

```text
/csa bind <token>
/csaero requirements
/csaero recorder
```

`/csaero requirements` 会显示当前服务端配置下的飞行认证条件。`/csaero recorder` 每次发放 1 个 `CSA Flight Recorder / CSA 飞行记录仪`。普通玩家必须已绑定 token，默认 5 秒冷却，背包和副手内默认最多持有 8 个；OP 绕过这些限制，方便测试。

玩家第一次进入服务器时，`aero-server` 会默认发放 `8` 个 `simulated:honey_glue / 蜂蜜胶` 和 `1` 个 `create:goggles / 工程师护目镜` 作为开局建造物资。蜂蜜胶不可堆叠，因此会分散为 8 个物品。两类物品使用独立发放标记，重连不会重复领取；已领过蜂蜜胶的玩家在更新后再次登录仍可领取护目镜。可通过 `starterHoneyGlue*` 和 `starterGoggles*` 配置项调整。

默认启用羊毛产出增强：剪羊一次固定掉落 `16` 个对应颜色羊毛，杀羊时只把羊毛掉落按 `8` 倍放大，羊肉保持原版掉落。可通过 `sheepProductionBoostEnabled`、`sheepShearingWoolCount` 和 `sheepDeathWoolDropMultiplier` 调整。

记录仪安装在航空学飞行器上并开始检测后，满足任一路线即可领取 flag：

- 稳定路线：连续 `stableCruiseTicks` 达标；水平速度不低于 `minHorizontalSpeed`，垂直速度、角速度、水平速度波动分别不超过配置阈值。
- 极速路线：`highSpeedClaimEnabled=true` 时，水平速度达到 `highSpeedHorizontalSpeed` 并连续保持 `highSpeedTicks`。
- 高度路线：`altitudeClaimEnabled=true` 时，飞行器高度达到 `altitudeY`，水平速度不低于 `altitudeMinHorizontalSpeed`，并连续保持 `altitudeTicks`。

放置者会在顶部 HUD 看到两行 bossbar：一行显示当前实际值和三条路线进度，另一行显示达标阈值。第一次开始显示时，聊天框只提示一次图例：`S=稳定路线`、`F=极速路线`、`A=高度路线`、`H=水平速度`、`Y=高度`、`V=垂直速度`、`R=角速度`、`D=水平速度波动`。HUD 默认每 5 tick 更新一次，可通过 `recorderHudIntervalTicks` 调整。

记录仪放置后会记录 `placer_uuid`、`placer_name`、`placed_at`、`claimed=false`。同一架航空学飞行器允许安装多个记录仪，每个记录仪独立归属放置者。认证达成后，服务端会调用：

```text
bridge.claimForUuid(placer_uuid, "aero_recorder_stable_cruise")
```

其中 reason 会按路线分别为 `aero_recorder_stable_cruise`、`aero_recorder_high_speed` 或 `aero_recorder_altitude`。

同一个记录仪只会触发一次。claim callback 成功时 flag 回传到 Ret2Shell 靶机页面；callback 未配置或失败时，在线玩家会收到游戏内私信兜底。

## Ret2Shell 配置

模板文件：

```text
ret2shell/checker/main.rx.example
ret2shell/register-to-mc.sh
```

### checker/main.rx

把 `ret2shell/checker/main.rx.example` 复制到 Ret2Shell 题目的 `checker/main.rx`，然后替换这些常量：

```rx
const ENCRYPT_KEY = "replace-with-a-random-24-byte-secret";
const TOKEN_SALT = "replace-with-a-different-random-token-salt";
const REGISTER_URL = "http://<minecraft-server-host>:18080/ret2shell/register";
const REGISTER_SECRET = "replace-with-the-same-registrationSecret";
```

`environ()` 会给每个 target 容器分发：

```text
FLAG
CSA_TOKEN
CSA_REGISTER_URL
CSA_REGISTER_SECRET
CSA_REGISTER_TTL_SECONDS
```

其中 `FLAG` 是动态 flag，`CSA_TOKEN` 是选手需要在 Minecraft 内绑定的 token。

### target 容器启动注册

target 容器启动时需要把 `CSA_TOKEN -> FLAG` 注册到 Minecraft 服务端 mod。最小做法是在容器入口脚本里执行：

```bash
ret2shell/register-to-mc.sh
```

脚本依赖 `curl`、`jq` 和 `sha256sum`。Alpine 镜像可用 `apk add --no-cache curl jq coreutils`，Debian/Ubuntu 镜像可用 `apt-get install -y curl jq coreutils`。

该脚本会发送：

```http
POST /ret2shell/register
X-CSA-Secret: <CSA_REGISTER_SECRET>
Content-Type: application/json
```

请求体：

```json
{
  "token": "CSA_TOKEN",
  "flag": "FLAG",
  "team_id": "TEAM_ID",
  "ttl_seconds": 86400,
  "callback_url": "http://target-pod-ip:8080/claim",
  "callback_secret": "per-target-random-secret"
}
```

如果你要让 flag 回传到容器页面，target 容器还需要实现：

```http
POST /claim
X-CSA-Callback-Secret: <callback_secret>
Content-Type: application/json
```

服务端 mod 会回传：

```json
{
  "token": "CSA_TOKEN",
  "flag": "flag{...}",
  "player_uuid": "minecraft-player-uuid",
  "player_name": "minecraft-player-name",
  "team_id": "optional-team-id"
}
```

如果不提供 `callback_url`/`callback_secret`，或者 callback 失败，服务端 mod 会退回到 Minecraft 私信发送 flag。

### Ret2Shell 容器服务建议

如果 target 容器要展示 token 和回传后的 flag，可以配置一个 HTTP 服务：

```text
容器名称：mc-flag-register
服务描述：Token
传输协议：TCP
应用协议：HTTP
服务端口：8080
```

容器页面建议显示：

- `/csa bind <CSA_TOKEN>`
- 靶机续期/重启后需要重新绑定 token 的提示
- 注册状态
- 回传后的 flag，并支持点击 flag 文本选中

## 通信流程

```text
Ret2Shell checker environ()
  -> target 容器环境变量 FLAG / CSA_TOKEN / CSA_REGISTER_*
  -> target 容器 POST Minecraft:18080/ret2shell/register
  -> 玩家 /csa bind <CSA_TOKEN>
  -> Minecraft 内触发题目条件
  -> 服务端 mod 找到玩家绑定 token 对应的 flag
  -> 优先 POST target 容器 /claim，失败时私信玩家
```

## 玩家流程

玩家进入 Minecraft 后先执行：

```text
/csa bind <token>
```

未绑定玩家会被 bind gate 限制在出生点或解绑时的位置，并附带黑暗效果。`/csa unbind` 会移除绑定并把玩家固定在当前位置；需要继续时重新启动/续期 Ret2Shell 靶机，复制新 token 再绑定。

## 安全注意事项

- 不要提交真实 `registrationSecret`、`ENCRYPT_KEY`、`TOKEN_SALT`、平台 IP 或比赛 bucket。
- 服务端注册端口建议只允许 Ret2Shell pod CIDR / Kubernetes 节点访问。
- `server` 和 `aero-server` 模组只放服务端；客户端包只放 `terminal`、`aero` 及其他玩法模组。
- target 容器内不要把 `FLAG` 直接打印到日志；注册成功后可以 `unset FLAG`，只保留页面回传所需状态。
