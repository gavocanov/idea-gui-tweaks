package org.cth.idea

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.ui.ColorUtil
import com.intellij.ui.tabs.impl.DefaultEditorTabsPainter
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.ui.tabs.impl.JBEditorTabsPainter
import com.intellij.ui.tabs.impl.ShapeTransform
import com.intellij.util.ReflectionUtil
import net.sf.cglib.proxy.Enhancer
import net.sf.cglib.proxy.MethodInterceptor
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.lang.reflect.Field

/**
 * Patch the Tabs Component to get the Material Design style
 */
class TabsPainter : BaseComponent {
    private val fillPathField: Field

    init {
        // Get the ShapeInfo class because it is protected
        val clazz = Class.forName("com.intellij.ui.tabs.impl.JBTabsImpl\$ShapeInfo")
        // Retrieve private fields of ShapeInfo class
        fillPathField = clazz.getField("fillPath")
    }

    override fun disposeComponent() = Unit
    override fun getComponentName(): String = "TabsPainter"

    override fun initComponent() {
        val bus = ApplicationManagerEx.getApplicationEx().messageBus
        val connect = bus.connect()
        connect.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val editor = event.newEditor
                if (editor != null) {
                    var component: Component? = editor.component
                    while (component != null) {
                        if (component is JBEditorTabs) {
                            patchPainter(component)
                            return
                        }
                        component = component.parent
                    }
                }
            }
        })
    }

    /**
     * Patch tabsPainter
     */
    private fun patchPainter(component: JBEditorTabs) {
        val painter = ReflectionUtil.getField(
            JBEditorTabs::class.java,
            component,
            JBEditorTabsPainter::class.java,
            "myDarkPainter"
        )

        if (painter is TabsPainter) return

        val tabsPainter = TabsPainter(component)

        val proxy = Enhancer.create(TabsPainter::class.java, MethodInterceptor { _, method, objects, _ ->
            val result = method.invoke(tabsPainter, *objects)
            if ("paintSelectionAndBorder" == method.name)
                paintSelectionAndBorder(objects, tabsPainter)
            result
        }) as TabsPainter

        ReflectionUtil.setField(
            JBEditorTabs::class.java,
            component,
            JBEditorTabsPainter::class.java,
            "myDefaultPainter",
            proxy
        )

        ReflectionUtil.setField(
            JBEditorTabs::class.java,
            component,
            JBEditorTabsPainter::class.java,
            "myDarkPainter",
            proxy
        )
    }

    /**
     * Paint tab selected and highlight border
     *
     * @param objects
     * @param tabsPainter
     */
    private fun paintSelectionAndBorder(objects: Array<Any>, tabsPainter: TabsPainter) {
        // Retrieve arguments
        val g2d = objects[0] as Graphics2D
        val selectedShape = objects[2]
        val tabColor = objects[4] as Color?

        val fillPath = fillPathField.get(selectedShape) as ShapeTransform
        // color me
        tabsPainter.fillSelectionAndBorder(g2d, fillPath, tabColor)
    }

    open class TabsPainter(tabs: JBEditorTabs? = null) : DefaultEditorTabsPainter(tabs) {
        companion object {
            val contrastColor: Color = EditorColorsManager.getInstance().globalScheme.defaultForeground
        }

        fun fillSelectionAndBorder(g: Graphics2D, selectedShape: ShapeTransform, tabColor: Color?) {
            g.color = tabColor ?: defaultTabColor
            g.fill(selectedShape.shape)
            myTabs.rootPane.background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        }

        override fun getBackgroundColor(): Color = EditorColorsManager.getInstance().globalScheme.defaultBackground
        override fun getDefaultTabColor(): Color = backgroundColor
        override fun getInactiveMaskColor(): Color = ColorUtil.withAlpha(contrastColor, 0.1)
        override fun getEmptySpaceColor(): Color = inactiveMaskColor
    }
}

