# 跳过 · 更新日志 / 交接备忘

> 原「交接备忘.md」，从 1.0.0 起改名为更新日志.hm：新版本改动记在最上面，后面保留项目全貌。

---

## v1.0.0（2026-10-19）

本次是一个大版本，从「只跳过开屏广告」扩展成「跳过 + 百宝箱」。

### 新增

1. **规则生态**
   - 接入 GKD 社区订阅：3029 条规则、覆盖 921 个应用，带严格匹配开关和 10 个分类开关
   - 规则包内置、可离线，分类可单独关；「功能类」默认关
2. **百宝箱（7 个模块）**
   - 连点器：取点准星、多点同时点击、点位标记、悬浮控制板（吸附屏幕边缘）
   - GitHub 加速：DNS 直接回答优选 IP（GitHub520 数据，MIT），基于 dnsjava 组包
   - 网站推荐：29 个工具站，随机抽取 + 复制链接
   - 清理内存：快速清理（系统接口）+ 深度清理（Shizuku am kill-all）
   - 图片解混淆：移植 PicEncrypt（MIT）6 种算法，混淆/解混淆 + 自检
   - 笔记：增删改、底色/文字色各 6 种、链接变蓝可点、粘贴剪贴板图片、独立/灵动岛悬浮窗
   - 今日人品：按日期定值抽签 + 评语 + 假滚动动画
3. **DNS 广告拦截（实验）**
   - 本地 VPN 只接管 DNS，命中回 NXDOMAIN，未命中转发上游
   - 黑白名单、手动录入、DNS 录入采集（红/绿两阶段）
   - 保护名单：图片/视频/聊天/系统域名永不拦
4. **悬浮窗增强**
   - 自定义图片支持 GIF / 动态 WebP（ImageDecoder）
   - 隐藏悬浮窗（1px / 全透明 / 不可点）
   - 全屏游戏自动隐藏（系统分类 + 58 个手游包名）
   - 隐藏通知（无横幅、不占锁屏）
5. **总开关升级**
   - 关掉时联动停止：跳过、连点、DNS、GitHub 加速、笔记窗、灵动岛、控制板、点位标记
6. **界面**
   - 新增「其他」页与「百宝箱」卡片（金色实心 / 金色描边 / 蓝色描边三种按钮）
   - 关于页新增「详细功能指南」（内置 guide.md）与制作人员
   - 强制竖屏

### 修复 / 优化

- 识别精度：节点面积 > 30% 不点；看图/评论/详情页不点；社区规则只收「关闭类」规则
- 熄屏重开误点：会话恢复只接受「像开屏页」的类名，熄屏/亮屏都重置
- 浏览器被误关：落地页保护改为只在跳到浏览器/商店且非黑名单时返回
- DNS 误拦图片：增加 NEVER_BLOCK 保护名单
- 灵动岛文字串台：关闭悬浮窗不再弹「已退出 DNS 录入」
- 规则包加载偶发 0 条：改回明文 JSON + 重试
- 悬浮窗尺寸下限 24dp、位置夹紧屏幕内

### 已知限制

- GitHub 加速只做 DNS 优选 IP，不做 TLS 中间人（Android 上不现实）
- 笔记悬浮窗的跨应用置顶未做完整实测
- Android 8+ 普通应用枚举不到其它进程，快速清理需要 Shizuku 才能彻底

---

# 跳过 · 项目交接备忘

> 写于 2026-10-19 上午。上下文可能被压缩，这份文件保证换一个会话也能无缝接上。

---

## 一、项目是什么

安卓「开屏广告自动跳过」工具，包名 `com.skipad`，版本 0.1.0。
基于无障碍服务识别并点击「跳过 / 关闭 / ×」，纯本地、默认无网络权限。

- 代码根目录：`<本机路径>
- 安装包产物：`dist\跳过-0.1.0-安装包.zip`
- 最新 APK：`app\build\outputs\apk\release\app-release.apk`

---

## 二、开发环境（已验证可用）

| 项 | 值 |
|---|---|
| JDK | `C:\Program Files\Android\openjdk\jdk-21.0.8`（`tools\env.ps1` 里设置） |
| Android SDK | 项目内 `.android-sdk`（build-tools 37.0.0） |
| AGP / Gradle / Kotlin | 9.4.1 / 9.8.1 / 2.4.21（AGP 9 内置 Kotlin，**不要**再加 `org.jetbrains.kotlin.android`） |
| compileSdk / minSdk / targetSdk | 37 / 26 / 36 |
| 签名 | `keystore.properties`（storepass `skipad123`，`app/skipad-release.jks`） |

构建命令：

```powershell
tools\build.cmd . :app:assembleRelease
```

打包安装包（APK + 说明书 + 示意图 + 校验值）：

```powershell
& "<本机路径> <脚本>
# 历史上用 .tmp\repack3.py 之类；dist 目录直接手改也行
```

---

## 三、当前测试设备：小米平板

手机（Redmi `<设备>`）已不在身边，**现在用平板测试**。

| 项 | 值 |
|---|---|
| 型号 | Xiaomi Pad `2410CRP4CC`（product `uke`） |
| 系统 | Android 16 / HyperOS `OS3.0.306.0.WOZCNXM` |
| 屏幕 | 2136×3200 @440dpi，**默认横屏**（截图是 3200×2136） |
| 内存 | 7.6 GB |
| 序列号 | `<设备>`（USB） |

### 平板操作要点（踩过的坑）

1. **装包会被 MIUI 拦**：弹出「USB 安装提示」→ 必须点「继续安装」。
   自动点法：后台起 `adb install`，立刻轮询 `uiautomator dump` 找 `text="继续安装"` 的 bounds，点它。
2. **`input tap` 坐标 = 截图坐标**（横屏 3200×2136），不是 `dumpsys input` 里那个 frame（那是旋转前的）。
3. **重装会丢悬浮窗权限**：装完要重新
   `adb shell appops set com.skipad SYSTEM_ALERT_WINDOW allow`，
   然后 `am force-stop com.skipad` + 重新启动，让 `restoreFloatingIfNeeded()` 把服务拉起来。
4. **重装会解绑无障碍**：
   `adb shell settings put secure enabled_accessibility_services com.skipad/com.skipad.service.SkipService`
   `adb shell settings put secure accessibility_enabled 1`
5. 用户**无法远程控制平板**，所有点击/截图由我来做；截图可丢到
   `<本机路径>）。

---

## 四、代码结构（关键文件）

```
app/src/main/java/com/skipad/
├── model/Rule.kt             规则模型 + JSON 解析（MatchSpec: ids/texts/descs/areas/markers/fallbackCorner）
├── engine/SkipEngine.kt      会话/窗口扩展/点击冷却/防误点/跳转拦截
├── engine/Matcher.kt         BFS 找节点；texts/descs 走正则，ids 支持通配
├── data/Prefs.kt             全部设置（SharedPreferences + StateFlow）
├── service/SkipService.kt    无障碍服务：事件、轮询、引导点击、黑名单
├── service/FloatingService.kt 悬浮窗：圆点 + 灵动岛面板 + DNS 录入标记窗
├── service/DnsFilterService.kt 实验：VpnService 骨架（目前只申请权限）
├── service/KeepAlive.kt      常驻通知/前台服务
├── service/BootReceiver.kt   开机自启 + 提醒
├── service/ServiceStatus.kt  无障碍是否开启
├── shizuku/ShizukuBridge.kt  Shizuku：exec / 禁组件 / 关链接 / 一键恢复无障碍
└── ui/MainActivity.kt        全部 UI（Compose 单文件，约 2000 行）
```

规则资源：`app/src/main/assets/rules/builtin.json`（约 20 条）
内置 Shizuku：`app/src/main/assets/shizuku.apk`（v13.6.0，2.4MB）

---

## 五、已实现功能（都在真机验证过）

**核心**
- 开屏跳过：启动后 12 秒窗口，事件驱动 + 400ms 轮询，广告晚到也能等
- 防误点：只点控件本身不点父容器；落地页 3.5 秒内跳走会撤回
- 多窗口读取（插屏/浮层在独立 window 也能读到）

**名单**
- 跳转拦截（开屏期跳出就按返回）
- 全程拦截（前台持续 900ms 扫描，命中关键词 + 关闭按钮）
- 黑名单（完全忽略）
- 三个名单都有搜索 + 刷新按钮

**保活**
- 悬浮窗（可拖拽、大小/颜色/透明度/贴图/音效、位置记忆）
- 划掉最近任务后 1 秒自启
- 开机自启（开关 + 通知提醒）
- 强制保活总开关（关掉即停一切）
- Shizuku 一键恢复无障碍

**灵动岛**
- 点圆点弹出第二窗口面板（独立 overlay，不动主圆点样式）
- 面板项可配置：打开 / 暂停拦截 / 清后台 / 打开指定应用 / DNS录入 / 关闭
- 点外部或 5 秒自动收起

**其他**
- Shizuku 高级禁用（禁广告组件、关 deeplink、内置安装包）
- 设备动作与方向权限「三跳自动化」（隐私与安全 → 其他权限 → 该权限）
- 小贴士（长按编辑）、统计、日志、节点树调试
- 两级恢复默认（悬浮窗设置内 / 关于页全局）

---

## 六、实验性 DNS 模块（已实现并在平板验证）

### 代码

| 文件 | 作用 |
|---|---|
| `dns/DnsProxy.kt` | DNS 代理内核：解析 IPv4/UDP:53，命中回 NXDOMAIN，未命中转发上游 |
| `dns/DnsPolicy.kt` | 内置名单：BASE_ADS（广告域名）+ HTTPDNS_ENDPOINTS（内置 DNS 端点） |
| `dns/DnsCapture.kt` | 录入缓冲区（录入模式下记录观察到的域名） |
| `service/DnsFilterService.kt` | VpnService：只路由 DNS（addDnsServer + 单条路由），自己 addDisallowedApplication 防套娃 |

### 工作方式

- TUN 里只有 `10.111.222.2/32` 一条路由 → 只有 DNS 进隧道，其它流量完全不受影响
- 上游 `223.5.5.5`，socket 必须 `protect()`
- 黑白名单：白名单优先；`dnsEnabled` 控制 BASE_ADS + 自录黑名单；`dnsHttpDnsBlock` 单独控制 HTTPDNS 端点
- 录入：灵动岛「DNS录入」→ 红色标记窗（广告阶段）→ 点一下提交黑名单并转绿（软件阶段）→ 再点提交白名单 → 长按退出

### 平板实测记录（2026-10-19）

| 测试 | 结果 |
|---|---|
| VPN 授权 + establish | ✅ `vpn established`，出现 tun0 10.111.222.1/32 |
| 正常域名 `www.baidu.com` | ✅ 正常解析 36.152.44.132 |
| `httpdns.bilivideo.com` | ✅ unknown host（HTTPDNS 抑制生效） |
| `alog.umeng.com` | ✅ unknown host |
| `a1.doubleclick.net` | ✅ unknown host（未缓存子域） |
| 录入：小米视频 → 点标记 | ✅ `capture commit 2 domains`，标记变绿 |
| 录入域名生效 | ✅ `zz9.img.cdn.mvideo.xiaomi.com` unknown host |
| 长按退出录入 | ✅ `capture stopped` |
| 名单管理 | ✅ 显示 / 单条删除 / 清空全部 |

### 已知限制（重要）

1. **DNS 缓存**：刚加进黑名单的域名，如果之前已经解析过，可能短时间内仍能用（系统 netd 缓存）
2. **录入不区分应用**：录到的是全局 DNS 查询，只按「阶段」区分广告/软件，不按前台包名归档
3. **HTTPDNS 只做域名级阻断**：如果 App 直接连 HTTPDNS 的 IP（硬编码），这里拦不到
4. **DoH / DoT**：完全绕开系统 DNS，无效
5. VPN 开着时状态栏有钥匙图标；后台被清后 VPN 会断，需要重新启动

### 手动录入（DNS 名单）

- 名单弹窗顶部有「手动输入域名」+「粘贴」按钮，下面两个按钮「加入黑名单 / 加入白名单」
- 归一化规则：去 `http://` `https://`、去掉 `/路径` 和 `:端口`、去 `*.` 前缀、结尾点；
  另外把中文输入法常见的全角 `。．｡－＿` 转成半角，空格全部剔除
  （这条是实测踩出来的：MIUI 输入法把 `.` 打成 `。`，导致校验失败）
- 回车（IME Done）也提交到黑名单，但 MIUI 键盘的回车不一定触发 IME action，按钮更可靠
- 输入非法会红字提示「格式不对：只填域名，比如 ads.example.com」

### 下一步可做

- 按前台包名归档域名（`SkipService` 已能拿到当前包名）
- HTTPDNS IP 段阻断（需要全流量接管，成本高）
- 把内置 BASE_ADS 扩展成可更新列表

## 七、GKD 规则包（已接入 v2：3029 条 / 921 App）

### 数据链路

```
reference/gkd-subscription-Lin-arm-2026-10-19.json5   （原始 4926 条）
        ↓ 转换脚本（.tmp/gkd_gen2.py，可重跑）
assets/rules/gkd.dat                                  （3029 条，gzip 后 60 KB）
        ↓ RuleRepo 解析 + 包名索引 + 分类过滤
运行时
```

### 覆盖率

| 项 | 数量 |
|---|---|
| 原始规则 | 4926 |
| **收录** | **3029**（61.5%） |
| 覆盖 App | **921** |
| 结构性规则（无法转换） | 1717 |
| 重复 | 178 |

分类分布：全屏广告 823 / 局部广告 668 / 功能类 443 / 分段广告 282 / 更新提示 251 /
开屏广告 243 / 通知提示 168 / 权限提示 58 / 评价提示 51 / 其他 30 / 青少年模式 12

### 转换策略（v2 比 v1 多做的一步）

- v1 只取「目标节点」属性 → 1429 条
- v2 如果目标节点没有属性，**向两边找最近的可用节点**（文字/描述/资源 id）→ +1600 条
- 仍无法处理的是「纯结构性选择器」，例如：
  `@View[width<102 && height<102][clickable=true] <3 [childCount=5] ...`
  这类没有文字/id，只能靠层级关系定位，我们的引擎表达不了

### 代码改动

| 位置 | 改动 |
|---|---|
| `Rule` | 新增 `category` 字段 |
| `Prefs` | `gkdRulesEnabled`、`disabledCategories`（默认关「功能类」） |
| `RuleRepo` | 内置 + GKD 合并；`byPkg` 索引；按分类过滤；`categoryStats()` |
| `SkipEngine` | 用 `repo.rulesFor(pkg)`，不再全量遍历 |
| UI | 新增第 6 个页签「**其他**」：规则包统计、分类开关、结构性规则说明、预留区 |

### 实测（平板）

- 「其他」页显示：共 3029 条 / 覆盖 921 App / 当前生效 2586 条（3029 − 443 功能类）
- 关掉「更新提示」→ 生效条数 2586 → 2335（正好 −251）✅
- 启动微信无副作用、无解析错误 ✅

### 体积优化（踩坑记录）

- 规则包 587 KB JSON → gzip 后 **60 KB**
- ⚠️ **不能叫 `.gz`**：AGP 的 assets 合并会把 `.gz` 自动解压成同名 `.json` 再打包，
  存进去的还是 588 KB（我踩过一次，APK 里一直是 gkd.json）
- 最终命名 `gkd.dat`（内容仍是 gzip），RuleRepo 用 `GZIPInputStream` 读，缺失时回退 `gkd.json`

### 「其他」页 UI（2026-10-19 晚更新）

顺序：社区规则包 → 为什么只转换了一部分（**淡红底**）→ **百宝箱（淡金底 + 金边，只有标题）** →
同类型软件推荐 → B 站第三方客户端 → DeepSeek 入口。
百宝箱是预留位，等接入第一个小工具。

- 第 6 个页签「其他」，主体是一张**可展开卡片**：统计 + GitHub 链接 + 启用开关 + 分类开关（展开后）
- 下方两张说明卡：为什么只转换一部分 / 预留区（以后塞新功能）
- GitHub 链接：https://github.com/Lin-arm/GKD_subscription （点击用系统浏览器打开）
- 另有一张「**其他同类型去广告软件推荐**」卡片（致敬 + 引导）：
  | 软件 | 链接 | 备注 |
  |---|---|---|
  | GKD | https://github.com/gkd-kit/gkd | 43k stars，规则包来源 |
  | 李跳跳 | https://github.com/rongzhiy/LiTiaotiao | 2.5k stars 社区镜像；官方 2023 年收律师函后无限期停更 |
  卡片底部有免责说明：仅致敬与方便查找，非官方分发，自行判断风险。
- 「**哔哩哔哩第三方客户端（无广告）**」卡片：
  | 客户端 | 链接 | 核实 |
  |---|---|---|
  | PiliPlus | https://github.com/bggRGjQaUbCoE/PiliPlus | 19.6k stars，GPL-3.0，每日有提交 |
  | BiliPai | https://github.com/jay3-yy/BiliPai | 4.9k stars，GPL-3.0，原生开发 |
- 「**DeepSeek 官方入口**」卡片：对话 https://chat.deepseek.com ，API 平台 https://platform.deepseek.com
  （实测点击后浏览器打开的是官方登录页「DeepSeek - 探索未至之境」）

### 与内置规则的关系（重要：不冲突，但有语义重叠）

| 维度 | 结论 |
|---|---|
| 引擎层面 | 不冲突。GKD 规则优先级 1005（本包名）/ 5，内置通用规则 0；先点中即返回，不会互相覆盖 |
| 同时命中 | 已加**会话级抑制**：一轮开屏点过一次后 2.5 秒内不再点第二次（全程拦截模式除外） |
| 语义重叠 | **有风险**：社区包里有 86 条规则只盯「取消/确定/同意/允许/拒绝/退出/暂不」这类裸按钮 |

根本原因：GKD 保留父子层级（知道这是广告弹窗里的「取消」），我们转换时丢了层级、只剩文字。
同样的「取消」在开屏 12 秒里，可能是应用自己的隐私弹窗 / 权限弹窗。

**已加安全过滤**（`RuleRepo.isUnsafe`）：社区规则里命中上述裸按钮文本的一律不收，内置规则不受影响。
效果：社区包 3029 条 → 实际生效 2513 条（3029 − 443 功能类 − 73 危险按钮）。

### 注意

- GKD 规则只在开屏 12 秒窗口内生效；要全程生效把 App 加进「全程拦截」
- 规则来自 GPL-3.0 社区订阅，个人自用可以；公开分发建议改成「用户自行导入」
- 转换脚本在 `.tmp/gkd_gen2.py`，订阅更新后重跑即可

## 八、关键决策与理由（避免重复讨论）

| 决策 | 原因 |
|---|---|
| 不做 hosts 方案 | 需要 root，HyperOS 只读分区 |
| DNS 走本地 VPN | 唯一不需要 root 的可行路径 |
| 抖动/静默后台取舍 | 无通知 → 进程优先级低 → 易被 MIUI 清；二者不可兼得 |
| 悬浮窗样式与灵动岛分离 | 避免修改主窗口参数导致 bug（用户明确要求） |
| 不申请联网权限（原设计） | 隐私卖点；DNS 模块引入后需说明清楚 |
| 国内镜像不收录 Shizuku | 腾讯/清华/阿里均无 GitHub Releases 镜像，改为内置 APK |

---

## 九、常用命令速查

```powershell
$ws = "<本机路径>
$env:TMP = "$ws\.tmp"; $env:TEMP = "$ws\.tmp"
$adb = "$ws\.android-sdk\platform-tools\adb.exe"
$dev = "<设备>"

# 装包（注意会弹 MIUI 确认）
& $adb -s $dev install -r "$ws\app\build\outputs\apk\release\app-release.apk"

# 重新授权无障碍 + 悬浮窗
& $adb -s $dev shell "settings put secure enabled_accessibility_services com.skipad/com.skipad.service.SkipService"
& $adb -s $dev shell "settings put secure accessibility_enabled 1"
& $adb -s $dev shell "appops set com.skipad SYSTEM_ALERT_WINDOW allow"

# 重启 App（触发悬浮窗恢复）
& $adb -s $dev shell am force-stop com.skipad
& $adb -s $dev shell am start -n com.skipad/com.skipad.ui.MainActivity

# 抓图到桌面
& $adb -s $dev shell screencap -p /sdcard/s.png
& $adb -s $dev pull /sdcard/s.png "<本机路径>

# 看日志
& $adb -s $dev logcat -d -s SkipEngine:V SkipService:V FloatingService:V DnsFilterService:V
```

UI 辅助脚本：`tools\ui.py`（`tap "文字"` / `shot` / `log`），没有写死设备号。

---

## 十、当前待办（按优先级）

0. **GKD 规则转换统计**（素材已在 `reference/`，尚未转换）
0. **环境门控补全**：小米保活引导、自启动引导、Shizuku 脚本也应加 `DeviceEnv.isMiui` 判断

1. **DNS 模块实装**：DnsProxy + HTTPDNS 名单 + 录入采集（骨架已在）
2. **GKD 规则接入**：先跑转换统计
3. 灵动岛细节：面板贴着圆点展开（现在会盖住圆点）、项顺序可调、图标
4. 平板适配：内容区限宽（现在 3200px 全宽撑开）、底部导航在横屏下过宽
5. 收尾：把 DNS/灵动岛写进说明书，重新打包

---



---

## 十一、GKD 原始素材位置

`reference/gkd-subscription-Lin-arm-2026-10-19.json5`（1.28 MB，4926 条）
转换与接入的完整说明见**第七节**，这里不再重复。

订阅更新后的重跑步骤：
1. 下载新版 json5 覆盖 `reference/` 里的文件
2. 跑 `.tmp/gkd_gen2.py`（改 REF 路径即可）生成 `assets/rules/gkd.json`
3. `tools\build.cmd . :app:assembleRelease`

## 十二、环境检测与功能门控（已实现）

新增 `data/DeviceEnv.kt`：

- `isMiui`：`MANUFACTURER`/`BRAND` 含 xiaomi/redmi/poco，或 `ro.miui.ui.version.name` 有值
- 非小米系时：
  1. 界面顶部出现**滚动提示条**：「本机不是小米 / 红米系统（xxx）：部分功能与此系统不适配，请谅解」（`basicMarquee`）
  2. 「设备动作与方向权限设置页」卡片按钮**置灰**，文案改成「当前系统不适配」，并说明手动去系统设置找
  3. `openSensorPermission()` 入口也加了判断，非 MIUI 直接 toast 返回（防止其它调用点绕过）

### 已门控清单

| 位置 | 非小米系表现 |
|---|---|
| 「设备动作与方向权限设置页」卡 | 按钮置灰「当前系统不适配」，文案换成手动找 |
| `openSensorPermission()` | 直接 toast 返回，防止别的入口绕过 |
| 「小米 / Redmi 保活设置」卡 | 标题变「保活建议」，四条 Tips 换成通用说法 |
| Shizuku「一键关闭（无线调试 + Shizuku）」 | 置灰，文案变「仅小米 / 红米可用」 |
| 开机自启弹窗 / 开关 Toast | 不再提 MIUI 自启动，改说「系统自启动 / 后台管理」 |
| 无障碍路径提示 | 去掉「更多设置」专有路径，改提示用设置搜索 |

### 提示条开关位置

关于页顶部（**仅非小米系可见**）：「系统不适配提示条」开关，关掉后顶部滚动条不再显示。
小米系设备上，滚动条和这个开关**都隐藏**。

---

## 十三、手机实测（Redmi 26020RN1AC / 720×1600 @320dpi / Android 16）

「跳过」在手机窄屏（360dp）上验证过：6 个页签、规则页、其他页、DNS 名单弹窗、特化页均正常。

### 这一轮修掉的手机缺陷

| 问题 | 原因 | 修法 |
|---|---|---|
| 悬浮圆点看不见也点不到（窗口只有 2×2 像素） | 历史设置 `floatingSize=1`，服务按 1dp×密度 建窗口 | 尺寸下限 **24dp**；设置弹窗滑杆范围 24–60 |
| 圆点可能跑到屏幕外 | 保存的 x/y 没有夹紧 | 按屏幕尺寸 `coerceIn` |
| 其他页「来源…」一行错位换行 | 三个 Text 挤一行 | 链接与许可证说明分成两行 |
| 无障碍路径提示「打 / 开」被拆行 | 长文本硬折 | 主动在「已下载的应用」后换行 |
| 一轮开屏被点两次（演示页 self-demo + 穿山甲兜底） | 全局冷却 900ms 刚好放行第二条规则 | 冷却提到 1800ms + 会话抑制 2500ms |
| 日志「耗时 9577 ms」明显不实 | 计时从会话开始算，跨 Activity 没重置 | 新增 `windowStart`，按当前页面计时（实测 115ms） |

### 手机 DNS 实测

VPN 授权 → `vpn established`，tun0 = 10.111.222.1/32；`www.baidu.com` 正常解析，
`httpdns.bilivideo.com` → unknown host；点「停止 DNS 拦截」后 tun0 消失。

## 十四、人工实测反馈与修复（2026-10-19 晚）

### 用户报的 4 个问题 → 根因与修法

| 问题 | 根因 | 修法 |
|---|---|---|
| B 站开屏偶发跳过失败 | 开屏广告本身就是偶发下发；GKD 规则在 MainActivityV2 上误命中 | 见下方「识别精度三连」 |
| DNS 误拦 B 站图片/动图/配饰 | 黑名单/录入把 CDN 域名拦了 | 新增 `DnsPolicy.NEVER_BLOCK`：hdslb/bilivideo/biliimg/qpic/gtimg/sinaimg 等图片视频聊天域名永不拦 |
| 熄屏重开视频后反复点评论区/详情页 | `recoverSession` 不看页面类型，把当前页当新会话 | recover 只接管「像开屏页」的类名；熄屏/亮屏都 `engine.reset()` |
| 打开图片时试图退出图窗 | 通用规则在看图页也动手 | 新增 `NON_SPLASH_HINTS`（image/gallery/photo/comment/detail/player…），命中直接不点 |
| 内置浏览器被关闭/退出 | 落地页保险按类名判断（含 browser/webview 就按返回） | 改成「跳转到了 SUSPICIOUS_TARGETS（浏览器/商店）且不是黑名单应用」才退 |
| 灵动岛关闭悬浮窗弹出「已退出 DNS 录入」 | `stopDnsCapture()` 无条件 Toast | 只有真正在录入时才提示；onDestroy 静默 |
| 划掉/打开应用抢触控 | 悬浮窗不可点时未加 NOT_TOUCHABLE、窗口 alpha 未归零 | 隐藏态：1px + LP alpha=0 + NOT_TOUCHABLE |

### 识别精度三连（这轮最重要的改动）

B 站实测发现社区规则会匹配 `*:id/pager`、`*:id/more`、`text="完善信息，获取精准推荐"`——
这些是**页面容器和广告素材本身**，点了就是自动打开视频/会员购。

1. `Matcher`：节点面积 > 屏幕 30% 一律不点（容器识别）
2. `RuleRepo` **严格模式（默认开）**：
   - 纯 id 规则：id 必须含 close/skip/dismiss
   - 带文字规则：文字必须含 关闭/跳过/不感兴趣/忽略/以后再说/暂不/知道了/× 等
3. 门控：`功能类` 分类默认关；`取消/确定/同意/允许/拒绝` 等裸按钮一律不收

实测效果（B 站连开两次）：
- 修之前：`clicked rule=gkd-605 (*:id/pager)` → 打开视频页；`gkd-634` → 打开会员购
- 修之后：**MainActivityV2 上零点击**，不再乱开页面

规则条数：3029 条 → 严格模式生效 **1201 条**；关掉严格 **2513 条**（其他页可切换）。

### 本轮新增功能

| 功能 | 位置 | 说明 |
|---|---|---|
| 隐藏悬浮窗 | 悬浮窗设置弹窗 | 1px、全透明、不可点；实测窗口 frame 1×1 |
| 全屏游戏自动隐藏 | 同上 + 默认开 | 系统 category + 58 个常见手游包名；实测 Dead Cells 前台 1px、回桌面恢复 24dp |
| 隐藏通知 | 首页保活卡 | 独立 channel `skip_running_hidden`，importance=MIN、`vis=SECRET`，无横幅无锁屏 |
| 详细日志 | 日志页右上「详细」 | 搜索框 + 完整字段（时间/应用/包名/规则/结果/详情） |
| 使用指南 | 关于页「详细功能指南」 | 内置 `assets/guide.md`，Compose 渲染标题/列表/表格 |

### 规则包加载的坑

之前把 gkd.json 压成 `gkd.dat`（gzip）省空间，实测**偶发读不完整**，
表现为「共 0 条规则 / 生效 0 条」。已改回明文 `gkd.json`（587KB），
加载器优先明文、gzip 只做兜底，并带一次重试。

## 十五、百宝箱 · 自动点击器（2026-10-19 晚）

### 为什么没直接用那个 APK

用户给了 `自动点击器 - v2.1.13.5.apk`（包名 `com.zidongdianji`，代码包 `com.autoclicker.clicker`，361 个类，未混淆）。
但它是**闭源商业软件**：内置穿山甲广告 SDK、Google Billing、还申请定位/电话状态权限。
反编译搬代码既侵权、又会把广告 SDK 拖进来，所以按它的功能自己实现。

GitHub 上也查了一圈：`Samo-samo/LibreTap`（GPL-3.0，2★）是唯一有明确许可证的；
`Azx8788/AutoClicker`、`ld1856/AutoAction`、`hanbao233xD/LittleClicker` 都没写许可证（默认保留所有权利），不能拷。
结论：自己写，功能对齐。

### 已实现（`SkipService` + `FloatingService`）

| 能力 | 说明 |
|---|---|
| 多点同时点击 | 一次 `GestureDescription` 里放 N 个 `StrokeDescription`，和主流自动点击器一致 |
| 间隔 | 50–2000ms，滑杆设置，实时生效 |
| 次数 | 0 = 一直点；点完自动停并 toast |
| 取点准星 | 全屏半透明 overlay，拖动十字到目标位置，实时显示坐标，确定/取消 |
| 入口 | 其他页「百宝箱」卡片（淡金 + 金边）→ 自动点击器对话框 |

### 真机实测（Redmi 26020RN1AC）

用系统计算器验证：取点选到「1」键（126,1292），间隔设 592ms、次数 10，点开始后切到计算器：
计算器表达式被正确追加数字（`…+8+8+101,711,111,111`），跑完自动停。
日志：`auto click start points=1 interval=592 times=10` → `auto click stop`。

**踩坑**：取点准星拖动的落点与实际十字中心有 ~25px 偏差（起始点没对准），
所以拖到 (128,1314) 才得到 (126,1292)；第一次取到 (126,1264) 落在按键缝隙里，点了没反应。

### 点位标记 + 悬浮控制板（2026-10-19 晚补）

- **点位标记**：`FloatingService.MarkerView`，全屏透明不可触层，用 Canvas 画「半透明蓝圆 + 白序号」，
  序号即点位顺序；点位增删后 `refreshAutoClickOverlays()` 立即重画。
- **控制板吸附边缘**：拖动结束自动吸到最近的左/右边缘（水平留 6dp），纵向夹在屏幕内；
  首次显示也会自动吸附。实测：初始吸附右侧 `[568,474][708,548]`，拖到中间松手后吸附左侧 `[12,863][152,937]`。
- **悬浮控制板**：深色圆角小条「▶ 连点 / ■ 停止」，可拖动（位置记忆 `autoclick_panel_x/y`），
  每 500ms 轮询 `SkipService.autoClicking` 更新文案与颜色；点一下即开/停，跨应用可用。
- 开关在「自动点击器」对话框里：显示点位标记（默认开）、悬浮控制板（默认关）。

实测：开控制板后点一下 → toast「连点开始（1 个点，间隔 592ms）」、按钮变「■ 停止」；
10 次跑完自动回到「▶ 连点」，日志 `auto click start/stop` 对得上。

### 百宝箱卡片改版 + 连点器总开关（2026-10-19 晚）

- 按钮文案固定为「连点器」（不再随总开关变）。
- 卡片不再整卡可点，改成「标题 + 金色实心按钮」，按钮样式对齐首页「打开设置页」那种：
  底色 `#D9B44A`、文字 `#3B2E0A`，文案随总开关显示「打开自动点击器 / 自动点击器（未启用）」。
- 新增 `autoClickEnabled` 总开关（**默认关**）：
  - 关：不显示点位标记、不显示控制板、`startAutoClickFromPrefs` 直接返回并 toast「先打开总开关」，
    如果正在跑会顺带停掉
  - 开：按「显示点位标记 / 悬浮控制板」两个子开关决定显示什么
- 开关位置：自动点击器对话框顶部。

实测：关着的时候桌面干净（无任何 overlay）；打开后按钮变「打开自动点击器」，
控制板「▶ 连点」和点位标记「1」同时出现。

### 还没做（如果后续要）

- ~~滑动/长按手势~~（用户决定不做，百宝箱小功能够用就行）
- 配置存档（多套点位命名保存、导入导出）
- 悬浮控制板（开始/暂停按钮浮在别的应用上）
- 随机偏移/随机间隔，降低机械感

## 十六、GitHub 加速（DNS 优选 IP，已验证）

### Watt Toolkit 的做法（读源码 BeyondDimension/SteamTools）

WinDivert 抓 UDP:53 → 命中规则伪造 A/AAAA 回环地址 → 应用连本机 → YARP 反向代理 TLS 终止 → 按 DoH 测速连最优 IP。
关键文件：`DnsInterceptor.cs`（改包+校验和）、`DnsAnalysisService`（DoH 测速）、`HostsFileService`。

### 我们的做法

Android 做不了 TLS 中间人（要装 CA），所以只做 DNS 回答改写：
命中 GitHub 域名直接回优选 IP，证书仍是官方证书。
数据：GitHub520 hosts（MIT）→ `assets/dns/github_hosts.txt`（40 条）。

### 踩坑（重要）

- **手拼 DNS 应答会被 netd 判为坏包**（客户端 unknown host），日志显示组包成功但解析失败
- 试过补 UDP checksum，结果伪首部按字节累加算法写错，整条 DNS 通道丢包，已回退
- **最终用 `dnsjava:dnsjava:3.6.5`（BSD-3，1087★）组包**，一次通过

### 实测

```
github.global.ssl.fastly.net -> 128.121.243.106   （我们映射的，自然解析是 74.86.226.234）
raw.githubusercontent.com    -> 185.199.111.133
github.com                   -> 20.205.243.166
www.baidu.com                -> 36.152.44.93      （不受影响）
```

### 入口

其他 → 百宝箱 → `[GitHub 加速]` 按钮 → 对话框（开关 / DNS 状态 / 映射条数 / 重启通道）。
开关打开会自动重启 DNS 通道。

## 十七、注意事项

- 用户很在意**不要随便杀进程**（他在用串流控制这台电脑时说过）
- 用户看不到平板，验证后要把截图放桌面
- 每次改完先 `assembleRelease`，装到平板，再验证
- 中文注释和 UI 文案风格：简洁、口语化、不装腔
