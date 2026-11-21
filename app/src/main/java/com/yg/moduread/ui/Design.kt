package com.yg.moduread.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计令牌。
 *
 * 存在的理由：改造前全项目散落着 10 余种 dp、7 种圆角、7 种字号，
 * 每个新组件都在重新发明间距。这里把可用的值收敛成有限几档，
 * 新代码只许从这里取，视觉一致性就不再依赖记性。
 *
 * 不定义就不该用的值（例如 7dp 间距）——需要新的档位时先改这里，
 * 而不是在调用点写魔法数字。
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

/** 圆角：只留 3 档，避免同类组件圆角不齐。 */
object Radius {
    /** 小元素：标签、胶囊、块号。 */
    val s = 6.dp

    /** 卡片、代码块、面板内部容器。 */
    val m = 12.dp

    /** 完全圆形的按钮与徽标，用足够大的值而非 50%。 */
    val pill = 999.dp
}

/** 字阶：6 档，覆盖从块号到空态标题的全部场景。 */
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
    /** 底部面板的顶部圆角：比卡片更圆，做出「掀起一张纸」的观感。 */
    val sheet = RoundedCornerShape(topStart = Radius.m, topEnd = Radius.m)
}

/** 可交互元素的最小触摸边长（Material 规范 48dp）。 */
val MinTouchTarget = 48.dp

/** 阅读区左右留白：正文每行字数控制在 20~24 个汉字，长文更好读。 */
val ReaderGutter = 20.dp
