# LOF/QDII 套利助手

一款用于LOF和QDII基金溢价套利的Android应用，帮助投资者实时监控基金溢价率，发现套利机会。

## 项目概述

由于各大金融APP已被要求不显示溢价率，本项目旨在：
- 获取T-1溢价率和实时溢价率
- 获取基金规模、成交量等信息
- 显示申购状态和申购限额
- 按溢价率排序，发现套利机会

## 功能特性

### 核心功能
- [x] LOF基金列表（按溢价率排序）
- [x] QDII基金列表（按溢价率排序）
- [x] T-1溢价率计算
- [x] 实时溢价率计算
- [x] 基金规模显示
- [x] 成交量显示
- [x] 申购状态（开放/暂停/限额）
- [x] 申购限额显示
- [x] 多API源切换
- [x] 手动刷新
- [x] 错误状态显示

### 待开发功能
- [ ] 自选基金收藏
- [ ] 溢价率预警
- [ ] 历史溢价率图表

## 技术架构

### 技术栈
| 组件 | 技术 |
|------|------|
| 开发语言 | Kotlin |
| 最低SDK | Android 5.0 (API 21) |
| 目标SDK | Android 14 (API 34) |
| 构建工具 | Gradle 8.x |
| 网络请求 | OkHttp + Retrofit |
| 异步处理 | Kotlin Coroutines |
| 状态管理 | LiveData / StateFlow |
| UI组件 | Material Design 3 |

### 项目结构
```
app/src/main/java/com/arbitrage/lofqdii/
├── data/                          # 数据层
│   ├── api/                       # API服务
│   │   ├── EastMoneyApi.kt        # 东方财富API（主）
│   │   ├── TianTianFundApi.kt     # 天天基金API（主）
│   │   ├── SinaFinanceApi.kt      # 新浪财经API（备用）
│   │   └── ApiSwitcher.kt         # API切换管理
│   ├── model/                     # 数据模型
│   │   ├── Fund.kt                # 基金基础信息
│   │   ├── FundDetail.kt          # 基金详情
│   │   └── PremiumInfo.kt         # 溢价率信息
│   └── repository/                # 数据仓库
│       └── FundRepository.kt      # 数据统一入口
├── ui/                            # UI层
│   ├── main/                      # 主页
│   │   ├── MainActivity.kt        # 主Activity
│   │   ├── FundListFragment.kt    # 基金列表Fragment
│   │   └── FundAdapter.kt         # 列表适配器
│   ├── detail/                    # 详情页
│   │   └── FundDetailActivity.kt  # 基金详情
│   └── settings/                  # 设置页
│       └── SettingsActivity.kt    # 设置页面
└── util/                          # 工具类
    ├── PremiumCalculator.kt       # 溢价率计算
    └── NetworkUtils.kt            # 网络工具
```

## API数据源

### 主数据源

| API | 用途 | 端点 |
|-----|------|------|
| 东方财富 | LOF/QDII列表、场内价格、成交量 | `https://push2.eastmoney.com/api/qt/clist/get` |
| 天天基金 | 基金净值、估算净值、申购状态 | `https://fundgz.1234567.com.cn/js/{code}.js` |

### 备用数据源

| API | 用途 | 端点 |
|-----|------|------|
| 新浪财经 | 实时行情 | `https://hq.sinajs.cn/list=f_{code}` |
| 腾讯财经 | 实时行情 | `https://web.sqt.gtimg.cn/q=` |

### API详情

#### 1. 东方财富 - 场内基金列表
```
GET https://push2.eastmoney.com/api/qt/clist/get

参数:
- fs: m:1+s:000001 (市场代码+证券代码)
- pn: 页码
- pz: 每页数量
- fields: 返回字段

返回字段:
- f12: 代码
- f14: 名称
- f2: 最新价
- f3: 涨跌幅
- f5: 成交量
- f6: 成交额
```

#### 2. 天天基金 - 基金估值
```
GET https://fundgz.1234567.com.cn/js/{fundcode}.js

返回格式: JSONP
{
  "fundcode": "基金代码",
  "name": "基金名称",
  "jzrq": "净值日期",
  "dwjz": "单位净值",
  "gsz": "估算净值",
  "gszzl": "估算涨幅"
}
```

## 溢价率计算

### T-1溢价率
```
T-1溢价率 = (T-1场内收盘价 - T-1基金净值) / T-1基金净值 × 100%
```

### 实时溢价率
```
实时溢价率 = (实时场内价格 - 估算净值) / 估算净值 × 100%
```

### QDII特殊处理
- 净值更新滞后（T+1或T+2）
- 考虑汇率影响
- 海外市场交易时间差异

## 构建与运行

### 环境要求
- JDK 17+
- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK 34

### 本地构建
```bash
# 克隆项目
git clone https://github.com/你的用户名/lof-qdii-arbitrage.git

# 进入项目目录
cd lof-qdii-arbitrage

# 构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 下载APK
在 [GitHub Releases](https://github.com/你的用户名/lof-qdii-arbitrage/releases) 页面下载最新版本APK。

## 开发进度

| 模块 | 状态 | 说明 |
|------|------|------|
| 项目结构 | ✅ 完成 | 目录结构已创建 |
| Gradle配置 | ✅ 完成 | Gradle 8.5 + Kotlin 1.9.22 |
| API服务层 | ✅ 完成 | 东方财富、天天基金、新浪财经 |
| 数据模型 | ✅ 完成 | Fund、FundDetail、PremiumInfo |
| 溢价率计算 | ✅ 完成 | PremiumCalculator工具类 |
| 主界面 | ✅ 完成 | ViewPager2 + TabLayout |
| 详情页 | ✅ 完成 | 基金详情展示 |
| 设置页 | ✅ 完成 | API切换 + 连接测试 |
| GitHub Actions | ✅ 完成 | 自动构建APK并发布Release |

## 更新日志

### v1.0.0
- ✅ 初始版本发布
- ✅ LOF/QDII基金列表，按溢价率排序
- ✅ T-1溢价率和实时溢价率计算
- ✅ 基金规模、成交量显示
- ✅ 申购状态和申购限额
- ✅ 多API源支持（东方财富、天天基金、新浪财经）
- ✅ GitHub Actions自动构建APK

## 免责声明

本项目仅供学习和研究使用，数据来源于公开API，不保证数据的准确性和及时性。投资有风险，入市需谨慎。使用本工具进行投资决策造成的损失，开发者不承担任何责任。

## 许可证

MIT License
