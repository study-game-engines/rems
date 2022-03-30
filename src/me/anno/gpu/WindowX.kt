package me.anno.gpu

import me.anno.input.Input
import me.anno.studio.StudioBase
import me.anno.ui.utils.WindowStack
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.glfw.GLFWKeyCallback
import org.lwjgl.system.MemoryUtil

open class WindowX(var title: String) {

    companion object {
        private val LOGGER = LogManager.getLogger(WindowX::class)
    }

    var pointer = 0L
    var width = 800
    var height = 700

    var lastUpdate = 0L

    private var oldTitle = title

    val windowStack = WindowStack()

    var lastCursor = 0L

    // where the mouse is
    // the default is before any mouse move was registered:
    // then the cursor shall start in the center of the window
    var mouseX = width * 0.5f
    var mouseY = height * 0.5f

    var contentScaleX = 1f
    var contentScaleY = 1f

    var isInFocus = false
    var isMinimized = false

    var needsRefresh = true

    var keyCallback: GLFWKeyCallback? = null
    var fsCallback: GLFWFramebufferSizeCallback? = null

    var shouldClose = false

    /**
     * set these to finite values, and the mouse should move there
     * do NOT set this target on multiple windows
     * */
    var mouseTargetX = Double.NaN
    var mouseTargetY = Double.NaN

    var savedWidth = 300
    var savedHeight = 300
    var savedX = 10
    var savedY = 10

    var enableVsync = true
    private var lastVsyncInterval = -1

    fun setVsyncEnabled(enabled: Boolean) {
        enableVsync = enabled
    }

    fun toggleVsync() {
        enableVsync = !enableVsync
    }

    open fun forceUpdateVsync() {
        val targetInterval = if (isInFocus) if (enableVsync) 1 else 0 else 2
        GLFW.glfwSwapInterval(targetInterval)
        lastVsyncInterval = targetInterval
    }

    open fun updateVsync() {
        val targetInterval = if (isInFocus) if (enableVsync) 1 else 0 else 2
        if (lastVsyncInterval != targetInterval) {
            GLFW.glfwSwapInterval(targetInterval)
            lastVsyncInterval = targetInterval
        }
    }

    fun toggleFullscreen() {
        // a little glitchy ^^, but it works :D
        val usedMonitor = GLFW.glfwGetWindowMonitor(pointer)
        if (usedMonitor == 0L) {
            savedWidth = width
            savedHeight = height
            val monitor = GLFW.glfwGetPrimaryMonitor()
            val mode = GLFW.glfwGetVideoMode(monitor)
            if (mode != null) {
                val windowX = intArrayOf(0)
                val windowY = intArrayOf(0)
                GLFW.glfwGetWindowPos(pointer, windowX, windowY)
                savedX = windowX[0]
                savedY = windowY[0]
                GLFW.glfwSetWindowMonitor(pointer, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate())
            }
        } else {
            GLFW.glfwSetWindowMonitor(
                pointer, MemoryUtil.NULL,
                savedX, savedY, savedWidth, savedHeight,
                GLFW.GLFW_DONT_CARE
            )
        }
        // this information gets lost otherwise...
        forceUpdateVsync()
    }

    fun updateMouseTarget(): Boolean {
        val robot = GFX.robot
        return if (mouseTargetX.isFinite() && mouseTargetY.isFinite()) {
            if (isInFocus) {
                GLFW.glfwSetCursorPos(pointer, mouseTargetX, mouseTargetY)
            } else if (robot != null) {
                val x = IntArray(1)
                val y = IntArray(1)
                GLFW.glfwGetWindowPos(pointer, x, y)
                robot.mouseMove(mouseTargetX.toInt() + x[0], mouseTargetY.toInt() + y[0])
            }
            mouseTargetX = -1.0
            mouseTargetY = -1.0
            true
        } else false
    }

    open fun requestAttention() {
        GLFW.glfwRequestWindowAttention(pointer)
    }

    open fun requestAttentionMaybe() {
        if (!isInFocus) {
            requestAttention()
        }
    }

    fun moveMouseTo(x: Float, y: Float) {
        mouseTargetX = x.toDouble()
        mouseTargetY = y.toDouble()
    }

    fun moveMouseTo(x: Double, y: Double) {
        mouseTargetX = x
        mouseTargetY = y
    }

    fun updateMousePosition() {
        val xs = DoubleArray(1)
        val ys = DoubleArray(1)
        GLFW.glfwGetCursorPos(pointer, xs, ys)
        Input.onMouseMove(this, xs[0].toFloat(), ys[0].toFloat())
    }

    fun updateTitle() {
        if (title != oldTitle) {
            GLFW.glfwSetWindowTitle(pointer, title)
            oldTitle = title
        }
    }

    open fun addCallbacks() {
        val window = pointer
        keyCallback = GLFW.glfwSetKeyCallback(window, object : GLFWKeyCallback() {
            override fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
                if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE)
                    GLFW.glfwSetWindowShouldClose(window, true)
            }
        })
        fsCallback = GLFW.glfwSetFramebufferSizeCallback(window, object : GLFWFramebufferSizeCallback() {
            override fun invoke(window: Long, w: Int, h: Int) {
                if (w > 0 && h > 0) {
                    StudioBase.addEvent {
                        if (w != width || h != height) {
                            width = w
                            height = h
                            // todo why is the screen becoming black for a few frames after changing the size?
                            Input.invalidateLayout()
                            Input.framesSinceLastInteraction = 0
                        }
                        Unit
                    }
                }
            }
        })
        GLFW.glfwSetWindowFocusCallback(window) { _: Long, isInFocus0: Boolean -> isInFocus = isInFocus0 }
        GLFW.glfwSetWindowIconifyCallback(window) { _: Long, isMinimized0: Boolean ->
            isMinimized = isMinimized0
            // just be sure in case the OS/glfw don't send it
            if (isMinimized0) needsRefresh = true
        }
        GLFW.glfwSetWindowRefreshCallback(window) { _: Long -> needsRefresh = true }

        // can we use that?
        // glfwSetWindowMaximizeCallback()
        val x = floatArrayOf(1f)
        val y = floatArrayOf(1f)
        GLFW.glfwGetWindowContentScale(window, x, y)
        contentScaleX = x[0]
        contentScaleY = y[0]

        // todo when the content scale changes, we probably should scale our text automatically as well
        // this happens, when the user moved the window from a display with dpi1 to a display with different dpi
        GLFW.glfwSetWindowContentScaleCallback(window) { _: Long, xScale: Float, yScale: Float ->
            LOGGER.info("Window Content Scale changed: $xScale x $yScale")
            contentScaleX = xScale
            contentScaleY = yScale
        }
    }

    /**
     * transparency of the whole window including decoration (buttons, icon and title)
     * window transparency is incompatible with transparent framebuffers!
     * may not succeed, test with getWindowTransparency()
     */
    fun setWindowOpacity(opacity: Float) {
        GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_FALSE)
        GLFW.glfwSetWindowOpacity(pointer, opacity)
    }

    /**
     * rendering special window shapes, e.g. a cloud
     * window transparency is incompatible with transparent framebuffers!
     * may not succeed, test with isFramebufferTransparent()
     */
    fun makeFramebufferTransparent() {
        GLFW.glfwSetWindowOpacity(pointer, 1f)
        GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE)
    }

    /**
     * transparency of the whole window including decoration (buttons, icon and title)
     */
    val windowTransparency: Float
        get() = GLFW.glfwGetWindowOpacity(pointer)

    fun requestExit() {
        GLFW.glfwSetWindowShouldClose(pointer, true)
    }

    val isFramebufferTransparent: Boolean
        get() = GLFW.glfwGetWindowAttrib(pointer, GLFW.GLFW_TRANSPARENT_FRAMEBUFFER) != GLFW.GLFW_FALSE


}