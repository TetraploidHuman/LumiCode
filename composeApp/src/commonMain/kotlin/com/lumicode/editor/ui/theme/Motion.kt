package com.lumicode.editor.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 「利落」的动效规格。
 *
 * 三条约束，全部界面共用：
 *  1. **短**：入场 170ms、退场 110ms。超过 250ms 就会开始显得"沉重"。
 *  2. **无回弹**：只有位置类变化（刻度滑动、箭头旋转）用高刚度弹簧，且阻尼接近临界，
 *     落到位置就停，不来回晃。
 *  3. **退场更快**：关掉一个东西永远比打开它快，这样连续操作不会排队等待。
 */
object RlMotion {
    /** 入场：起手快、收尾稳（emphasized decelerate）。 */
    val Sharp: Easing = CubicBezierEasing(0.16f, 0.84f, 0.24f, 1f)

    /** 退场：起步就快，不减速。 */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    const val ENTER = 170
    const val EXIT = 110

    fun <T> enter(duration: Int = ENTER): FiniteAnimationSpec<T> = tween(duration, easing = Sharp)

    fun <T> exit(duration: Int = EXIT): FiniteAnimationSpec<T> = tween(duration, easing = Exit)

    /**
     * 位置类变化：刻度滑动、树节点箭头旋转。
     * 刚度高、阻尼 0.9，是"啪"地一下到位而不是弹两下。
     */
    fun <T> snap(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessHigh * 3f,
    )
}
