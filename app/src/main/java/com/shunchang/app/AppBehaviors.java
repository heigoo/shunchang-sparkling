package com.shunchang.app;

import com.lynx.tasm.behavior.Behavior;
import com.lynx.tasm.behavior.LynxContext;
import com.lynx.tasm.behavior.shadow.ShadowNode;
import com.lynx.tasm.behavior.ui.LynxUI;
import com.lynx.xelement.blur.BehaviorGenerator;
import com.lynx.xelement.input.LynxUIInput;
import com.lynx.xelement.input.LynxUIInputShadowNode;
import com.lynx.xelement.input.LynxUITextArea;
import com.lynx.xelement.input.LynxUITextAreaShadowNode;
import com.lynx.xelement.svg.LynxUISVG;

import java.util.ArrayList;
import java.util.List;

/**
 * Lynx 把 input / textarea 拆到了 xelement-input 中，宿主要显式注册这些行为，
 * 否则元素树和原生视图树会错位。
 */
final class AppBehaviors {
    private AppBehaviors() {
    }

    static List<Behavior> create() {
        List<Behavior> behaviors = new ArrayList<>();

        behaviors.add(new Behavior("input") {
            @Override
            public LynxUI createUI(LynxContext context) {
                return new LynxUIInput(context);
            }

            @Override
            public ShadowNode createShadowNode() {
                return new LynxUIInputShadowNode();
            }
        });

        behaviors.add(new Behavior("textarea") {
            @Override
            public LynxUI createUI(LynxContext context) {
                return new LynxUITextArea(context);
            }

            @Override
            public ShadowNode createShadowNode() {
                return new LynxUITextAreaShadowNode();
            }
        });

        behaviors.add(new Behavior("svg") {
            @Override
            public LynxUI createUI(LynxContext context) {
                return new LynxUISVG(context);
            }

            @Override
            public LynxUI createUIWithParams(LynxContext context, Object params) {
                return new LynxUISVG(context, params);
            }
        });

        // 原生背景模糊元件 <blur-view>。
        behaviors.addAll(BehaviorGenerator.getBehaviors());

        return behaviors;
    }
}
