package net.eclipce.transpondersnails.entity.animations;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Animation definitions for the Horned Den Den Mushi.
 *
 * The keyframe data is identical to {@link DenDenMushiAnimationDefinitions} —
 * kept as a separate class so the horned variant can diverge independently
 * in the future without touching the base DDM.
 */
public class HornedDenDenMushiAnimationDefinitions {

    public static final AnimationDefinition idle = AnimationDefinition.Builder.withLength(2.0F).looping()
            .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("shell", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.degreeVec(-2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("shell", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("upper", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.0F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(2.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),   AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition walking = AnimationDefinition.Builder.withLength(1.5F).looping()
            .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),        AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.07F, 0.222F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),        AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("eyeballs", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(3.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("eyeballs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),       AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, 0.28F, 0.4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),       AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("shell", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,  KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.degreeVec(-2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("shell", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("upper", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(1.5F,  KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR)
            ))
            .build();

    public static final AnimationDefinition scared_walking = AnimationDefinition.Builder.withLength(0.75F).looping()
            .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,    KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(3.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("eyes", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,    KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),        AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.posVec(0.0F, 0.07F, 0.222F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),        AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("eyeballs", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,    KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(3.5F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("eyeballs", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,    KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),       AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.posVec(0.0F, 0.28F, 0.4F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),       AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("head", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,    KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("shell", new AnimationChannel(AnimationChannel.Targets.ROTATION,
                    new Keyframe(0.0F,    KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.degreeVec(-2.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.degreeVec(0.0F, 0.0F, 0.0F),  AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("shell", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,    KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("body", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F, KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F), AnimationChannel.Interpolations.LINEAR)
            ))
            .addAnimation("upper", new AnimationChannel(AnimationChannel.Targets.POSITION,
                    new Keyframe(0.0F,    KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.3333F, KeyframeAnimations.posVec(0.0F, -0.15F, 0.0F), AnimationChannel.Interpolations.LINEAR),
                    new Keyframe(0.75F,   KeyframeAnimations.posVec(0.0F, 0.0F, 0.0F),    AnimationChannel.Interpolations.LINEAR)
            ))
            .build();
}