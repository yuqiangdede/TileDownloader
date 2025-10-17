# Tile Downloader 地图切片批量下载器

Tile Downloader 是一款帮助开发者和地图爱好者批量下载瓦片地图的 Java 工具。程序支持主流的在线地图服务（OpenStreetMap、ArcGIS、OpenSeaMap、高德地图、谷歌地图等），并提供命令行与 Swing 图形界面两种使用方式，可自定义下载范围、缩放级别及线程并发度。

## 核心特性
- 按照经纬度范围自动换算瓦片编号，支持多级缩放（zoom）。
- 同时兼容 ZXY（OpenStreetMap 系）与 ZYX（ArcGIS 系）两种 URL 模板。
- 支持命令行/GUI 双模式，所见即所得配置下载任务。
- 支持 SOCKS 代理、断点续传及已存在瓦片跳过策略。
- 线程池并行下载，实时输出进度与失败重试信息。
- 可通过配置文件或命令行参数快速扩展新的地图源。

## 系统要求
- JDK 20 及以上版本
- Maven 3.9+
- 可访问目标地图服务的稳定网络

## 快速开始

1. **构建项目**
   ```powershell
   mvn clean package
   ```
   命令完成后会在 `target/` 目录生成 `MapDownloader-1.0-SNAPSHOT.jar` 可执行文件。

2. **命令行模式**
   ```powershell
   java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.downloader.TileDownloader `
     --base=D:\tiles `
     --lon=117,118 `
     --lat=38,39 `
     --zoom=0,13 `
     --sources=osm-standard,gaode-roadnet
   ```
   - `--base`：瓦片保存根目录，默认 `D:\temp`
   - `--lon` / `--lat`：经纬度范围（起始,结束）
   - `--zoom`：最小与最大缩放级别
   - `--sources`：逗号分隔的地图源 ID，`all` 表示全部

3. **图形界面模式**
   ```powershell
   java -cp target/MapDownloader-1.0-SNAPSHOT.jar com.yuqiangdede.downloader.TileDownloaderUI
   ```
   GUI 模式可通过表单填写经纬度、缩放级别以及需要的地图源，并实时查看预估的瓦片数量与下载进度。

## 支持的地图源
命令行中可通过 `--sources` 选择所需来源，GUI 中可复选所需条目。

### OpenStreetMap（ZXY）
| ID | 说明 |
| --- | --- |
| `osm-standard` | OpenStreetMap Standard |
| `osm-hot` | Humanitarian 样式 |
| `cyclosm` | CyclOSM 骑行地图 |

### ArcGIS（ZYX）
| ID | 说明 |
| --- | --- |
| `arcgis-topo` | ArcGIS Topographic |
| `arcgis-imagery` | ArcGIS Imagery |

### OpenSeaMap（ZXY）
| ID | 说明 |
| --- | --- |
| `seamap-base` | 海图底图 |
| `seamap-seamark` | 航标图层 |

### 高德地图（ZXY）
| ID | 说明 |
| --- | --- |
| `gaode-satellite` | 卫星图 |
| `gaode-hybrid` | 混合图（卫星 + 标注） |
| `gaode-roadnet` | 道路网 |
| `gaode-light` | 浅色底图 |
| `gaode-light-poi` | 浅色底图（POI 加粗） |

### 谷歌地图（ZXY）
| ID | 说明 |
| --- | --- |
| `google-vector` | 矢量地图 |
| `google-satellite` | 卫星图 |
| `google-hybrid` | 混合图 |
| `google-terrain` | 地形图 |
| `google-terrain-labels` | 地形图（带标注） |
| `google-roads` | 道路图层 |

## 关键配置参数
| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `BASE_DIRECTORY` | 瓦片保存根目录 | `D:\temp` |
| `START_LON` / `END_LON` | 经度范围 | `117` / `118` |
| `START_LAT` / `END_LAT` | 纬度范围 | `38` / `39` |
| `MIN_ZOOM` / `MAX_ZOOM` | 缩放级别 | `0` / `13` |
| `THREADS` | 下载线程数 | `16` |
| `PROGRESS_STEP` / `LARGE_PROGRESS_STEP` | 进度输出频率 | `100` / `10_000` |
| `BUFFER_SIZE` | 单次读取字节数 | `8192` |
| `DOWNLOAD_TYPE` | 下载模式：`all` / `cli` / `ui` / `sources` | `all` |

命令行启动时可通过参数覆盖上述配置；若需常驻配置，可修改源码中的默认常量或在未来加入配置文件支持。

## 工作流程
1. 根据用户输入的经纬度范围与缩放级别计算所有目标瓦片编号。
2. 遍历所选地图源，拼装对应的 URL 模板。
3. 线程池并发请求瓦片数据，检测本地是否已存在避免重复下载。
4. 对下载失败的瓦片根据策略重试，并在命令行或 GUI 中输出实时进度。

## 使用建议
- 下载大范围数据前先尝试较小范围，确认网络与目标目录写入权限。
- 合理调整线程数，避免过多并发导致目标服务触发限流。
- 建议将不同下载任务保存在独立目录，便于管理与备份。
- 若需要新增地图源，只需在 `TILE_SOURCES` 配置中追加对应 URL 模板。

## 许可证
本项目基于 MIT License 开源，详情参见仓库中的 `LICENSE` 文件。
