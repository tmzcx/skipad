# 第三方组件与数据来源

本项目使用了以下开源项目 / 数据，在此致谢并保留其许可证要求。

| 组件 | 用途 | 许可证 | 链接 |
|---|---|---|---|
| GKD 订阅规则 | 社区广告跳过规则（转换为内置 JSON） | GPL-3.0 | https://github.com/gkd-kit/subscription |
| PicEncrypt | 图片混淆/解混淆算法 | MIT | https://github.com/jiarandiana0307/PicEncrypt |
| GitHub520 | GitHub 优选 IP 数据 | MIT | https://github.com/521xueweihan/GitHub520 |
| dnsjava | DNS 报文解析与构造 | BSD-3-Clause | https://github.com/dnsjava/dnsjava |
| Shizuku | 可选的高级权限能力（未随仓库分发） | Apache-2.0 | https://github.com/RikkaApps/Shizuku |

## 说明

- `app/src/main/assets/rules/gkd.json` 由 GKD 订阅离线转换而来，遵循 GPL-3.0；
  本项目整体以 GPL-3.0 发布，以满足其许可证要求。
- `app/src/main/java/com/skipad/image/` 下的 6 个算法类来自 PicEncrypt（MIT），
  仅修改了 package 声明。
- `app/src/main/assets/dns/github_hosts.txt` 来自 GitHub520（MIT）。
- 图片混淆算法的原始版权归 PicEncrypt 作者所有。
