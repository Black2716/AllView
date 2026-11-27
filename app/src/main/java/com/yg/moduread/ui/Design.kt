package com.yg.moduread.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌 —— 全应用唯一的取值来源。
 *
 * 存在的理由：改造前全项目散落着 10 余种 dp、7 种圆角、7 种字号，
 * 每个新组件都在重新发明间距。这里把可用的值收敛成有限几档，
 * 新代码只许从这里取，视觉一致性就不再依赖记性。
 *
 * 不定义就不该用的值（例如 7dp 间距）——需要新的档位时先改这里，
 * 而不是在调用点写魔法数字。
 *
 * 2026-11 UI 重塑后：这些令牌同时喂给 MaterialTheme（见 Theme.kt 的
 * AppTypography / AppShapes），M3 自带组件与自绘组件因此共用同一套
 * 圆角与字阶 —— 这是「两套设计系统并存」的根治办法。
 */

/** 间距：4dp 栅格，只留 6 档。 */
object Sp {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 圆角：只留 4 档，避免同类组件圆角不齐。 */
object Radius {
    /** 小元素：标签、胶囊、块号、输入框。 */
    val s = 6.dp

    /** 卡片、代码块、面板内部容器。 */
    val m = 12.dp

    /** 大容器：底部面板、整页卡片。比 m 更圆，做出「掀起一张纸」的观感。 */
    val l = 28.dp

    /** 完全圆形的按钮与徽标，用足够大的值而非 50%。 */
    val pill = 999.dp
}

/** 字阶：7 档，覆盖从块号到空态标题的全部场景。 */
object Type {
    /** 块号、图注、进度点等最小文字。 */
    val label = 11.sp

    /** 次要说明、时间戳、语言标签。 */
    val caption = 12.sp

    /** 面板内的正文（设置项、搜索片段）。 */
    val body = 13.sp

    /** 列表主文字（目录项、最近文件）。 */
    val bodyLarge = 14.sp

    /** 面板标题。 */
    val title = 16.sp

    /** 首页品牌字、区块大标题。 */
    val heading = 20.sp

    /** 空态主标题。 */
    val display = 28.sp
}

/**
 * 常用形状。放在这里而不是每个调用点 new 一次，
 * 保证同类容器永远同一个圆角。
 */
object Shapes {
    val card = RoundedCornerShape(Radius.m)
    val chip = RoundedCornerShape(Radius.s)
    val pill = RoundedCornerShape(Radius.pill)
    val sheet = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l)
}

/**
 * 透明度分档。
 *
 * 存在的理由：改造前阅读界面的半透明都用裸浮点数
 * （0.75 进度条、0.6 分隔线、0.35 置灰图标），同一个「很淡的灰」
 * 在不同文件里可能写成 0.3、0.35 或 0.4，暗色主题下尤其容易花。
 * 这里只保留实际用到的几档，值与原调用点逐一对应，不引入视觉变化。
 */
object Alpha {
    /** 置灰：功能不可用，但文字仍需可读（约 1/3）。 */
    const val faint = 0.35f

    /** 禁用态控件（Material 规范 0.38）。 */
    const val disabled = 0.38f

    /** 分隔线、辅助线（约 3/5）。 */
    const val soft = 0.6f

    /** 进度条填充：比主色淡一档，避免喧宾夺主。 */
    const val progress = 0.75f

    /** 装饰性色块（引用块竖条、表格分隔线）。改造前这里写成 0.65f / 0.7f 两个裸值。 */
    const val decorative = 0.65f

    /** 更淡的装饰线（表格行分隔）。 */
    const val hairline = 0.7f

    /** 选中态底色（底栏当前页图标、标签条）：叠在主背景上仍能看出层级。 */
    const val selected = 0.55f

    /** 压在图像/幻灯片上的信息条底色：要挡住底下的内容，又不能像纯色块。 */
    const val overlay = 0.9f

    /** 全屏遮罩：几乎不透，只留一点点环境色（PDF 全屏看图时用）。 */
    const val scrim = 0.97f
}

/** 阴影/托起层级。M3 用 tonal elevation，这里只给需要真阴影的浮层留档。 */
object Elevation {
    // Dp 不是可 const 的类型（Kotlin 只允许基本类型与 String），这里用 val
    val flat = 0.dp
    val card = 1.dp
    val sheet = 8.dp
    val overlay = 12.dp
}

/** 动效时长（毫秒）。全部短于 300ms：面板与状态切换不该让人等。 */
object Motion {
    /** 颜色、透明度这类「一眼就完」的变化。 */
    const val fast = 120

    /** 位移、淡入淡出的默认值。 */
    const val base = 180

    /** 大块内容进场（首页卡片、面板内容）。 */
    const val slow = 260
}

/** 发丝线厚度。1dp 在高密度屏上仍能落在物理像素上。 */
val Hairline = 1.dp

/** 可交互元素的最小触摸边长（Material 规范 48dp）。 */
val MinTouchTarget = 48.dp

/** 阅读区左右留白：正文每行字数控制在 20~24 个汉字，长文更好读。 */
val ReaderGutter = 20.dp

/**
 * 底部面板里长列表的高度上限。
 *
 * 取屏幕高度的比例不方便（面板本身就在 ModalBottomSheet 里，拿不到稳定的可用高度），
 * 用一个固定值更可控：420dp 在最小的 5 寸屏上也只占七成，仍有空间滑走收起。
 */
val SheetListMaxHeight = 420.dp
