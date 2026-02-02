/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.javafx.sg.prism;

import com.sun.javafx.geom.BackdropRegionContainer;
import com.sun.javafx.geom.BackdropRegionPool;
import javafx.scene.CacheHint;
import java.util.ArrayList;
import java.util.List;
import com.sun.glass.ui.Screen;
import com.sun.javafx.geom.BaseBounds;
import com.sun.javafx.geom.BoxBounds;
import com.sun.javafx.geom.DirtyRegionContainer;
import com.sun.javafx.geom.DirtyRegionPool;
import com.sun.javafx.geom.Point2D;
import com.sun.javafx.geom.RectBounds;
import com.sun.javafx.geom.Rectangle;
import com.sun.javafx.geom.transform.Affine3D;
import com.sun.javafx.geom.transform.BaseTransform;
import com.sun.javafx.geom.transform.GeneralTransform3D;
import com.sun.javafx.geom.transform.NoninvertibleTransformException;
import com.sun.prism.CompositeMode;
import com.sun.prism.Graphics;
import com.sun.prism.GraphicsPipeline;
import com.sun.prism.RTTexture;
import com.sun.prism.ReadbackGraphics;
import com.sun.prism.Surface;
import com.sun.prism.impl.PrismSettings;
import com.sun.scenario.effect.Blend;
import com.sun.scenario.effect.Effect;
import com.sun.scenario.effect.FilterContext;
import com.sun.scenario.effect.ImageData;
import com.sun.scenario.effect.impl.prism.PrDrawable;
import com.sun.scenario.effect.impl.prism.PrEffectHelper;
import com.sun.scenario.effect.impl.prism.PrFilterContext;
import com.sun.javafx.logging.PulseLogger;
import static com.sun.javafx.logging.PulseLogger.PULSE_LOGGING_ENABLED;

/**
 * NGNode is the abstract base class peer of Node, forming
 * the basis for Prism and Scenario render graphs.
 * <p>
 * During synchronization, the FX scene graph will pass down to us
 * the transform which takes us from local space to parent space, the
 * content bounds (ie: geom bounds), and the transformed bounds
 * (ie: boundsInParent), and the clippedBounds. The effect bounds have
 * already been passed to the Effect peer (if there is one).
 * <p>
 * Whenever the transformedBounds of the NGNode are changed, we update
 * the dirtyBounds, so that the next time we need to accumulate dirty
 * regions, we will have the information we need to make sure we create
 * an appropriate dirty region.
 * <p>
 * NGNode maintains a single "dirty" flag, which indicates that this
 * node itself is dirty and must contribute to the dirty region. More
 * specifically, it indicates that this node is now dirty with respect
 * to the back buffer. Any rendering of the scene which will go on the
 * back buffer will cause the dirty flag to be cleared, whereas a
 * rendering of the scene which is for an intermediate image will not
 * clear this dirty flag.
 */
public abstract class NGNode {
    private final static GraphicsPipeline pipeline =
        GraphicsPipeline.getPipeline();

    private final static Boolean effectsSupported =
        (pipeline == null ? false : pipeline.isEffectSupported());

    public static enum DirtyFlag {
        CLEAN,
        // Means that the node is dirty, but only because of translation
        DIRTY_BY_TRANSLATION,
        DIRTY
    }

    /**
     * Used for debug purposes. Set during sync.
     */
    private String name;

    /**
     * Temporary bounds for use by this class or subclasses, designed to
     * reduce the amount of garbage we generate. If we get to the point
     * where we have multi-threaded rasterization, we might need to make
     * this per-instance instead of static.
     */
    private static final BoxBounds TEMP_BOUNDS = new BoxBounds();
    private static final RectBounds TEMP_RECT_BOUNDS = new RectBounds();
    private static final RectBounds TEMP_RECT_BOUNDS_2 = new RectBounds();
    private static final RectBounds TEMP_RECT_BOUNDS_3 = new RectBounds();
    private static final Rectangle TEMP_RECTANGLE = new Rectangle();
    protected static final Affine3D TEMP_TRANSFORM = new Affine3D();

    /**
     * Statics for defining what the culling bits are. We use 2 bits to
     * determine culling status
     */
    static final int DIRTY_REGION_INTERSECTS_NODE_BOUNDS = 0x1;
    static final int DIRTY_REGION_CONTAINS_NODE_BOUNDS = 0x2;
    static final int DIRTY_REGION_CONTAINS_OR_INTERSECTS_NODE_BOUNDS =
            DIRTY_REGION_INTERSECTS_NODE_BOUNDS | DIRTY_REGION_CONTAINS_NODE_BOUNDS;

    /**
     * The transform for this node. Although we are handed all the bounds
     * during synchronization (including the transformed bounds), we still
     * need the transform so that we can apply it to the clip and so forth
     * while accumulating dirty regions and rendering.
     */
    private BaseTransform transform = BaseTransform.IDENTITY_TRANSFORM;

    /**
     * The cached transformed bounds. This is never null, but is frequently set
     * to be invalid whenever the bounds for the node have changed. These are
     * "complete" bounds, that is, with transforms and effect and clip applied.
     * Note that this is equivalent to boundsInParent in FX.
     */
    protected BaseBounds transformedBounds = new RectBounds();

    /**
     * The cached bounds. This is never null, but is frequently set to be
     * invalid whenever the bounds for the node have changed. These are the
     * "content" bounds, that is, without transforms or filters applied.
     */
    protected BaseBounds contentBounds = new RectBounds();

    /**
     * The cached backdrop-effect bounds, which are computed as necessary for this node
     * and are invalidated when the node bounds or the backdrop effect has changed.
     */
    private BaseBounds backdropBounds = new RectBounds();

    /**
     * Indicates whether {@link #backdropBounds} is invalid and needs to be recomputed.
     */
    private boolean backdropBoundsInvalid = true;

    /**
     * We keep a reference to the last transform bounds that were valid
     * and known. We do this to significantly speed up the rendering of the
     * scene by culling and clipping based on "dirty" regions, which are
     * essentially the rectangle formed by the union of the dirtyBounds
     * and the transformedBounds.
     */
    BaseBounds dirtyBounds = new RectBounds();

    /**
     * Whether the node is visible. We need to know about the visibility of
     * the node so that we can determine whether to cull it out, and perform
     * other such optimizations.
     */
    private boolean visible = true;

    /**
     * Indicates that this NGNode is itself dirty and needs its full bounds
     * included in the next repaint. This means it is dirty with respect to
     * the back buffer. We don't bother differentiating between bounds dirty
     * and visuals dirty because we can simply inspect the dirtyBounds to
     * see if it is valid. If so, then bounds must be dirty.
     */
    protected DirtyFlag dirty = DirtyFlag.DIRTY;

    /**
     * The parent of the node. In the case of a normal render graph node,
     * this will be an NGGroup. However, if this node is being used as
     * a clip node, then the parent is the node it is the clip for.
     */
    private NGNode parent;

    /**
     * True if this node is a clip. This means the parent is clipped by this node.
     */
    private boolean isClip;

    /**
     * The node used for specifying the clipping shape for this node. If null,
     * then there is no clip.
     */
    private NGNode clipNode;

    /**
     * The opacity of this node.
     */
    private float opacity = 1f;

    /**
     * The view order of this node.
     */
    private double viewOrder = 0;

    /**
     * The blend mode that controls how the pixels of this node blend into
     * the rest of the scene behind it.
     */
    private Blend.Mode nodeBlendMode;

    /**
     * The depth test flag for this node. It is used when rendering if the window
     * into which we are rendering has a depth buffer.
     */
    private boolean depthTest = true;

    /**
     * A filter used when the node is cached. If null, then the node is not
     * being cached. While in theory this could be created automatically by
     * the implementation due to some form of heuristic, currently we
     * only set this if the application has requested that the node be cached.
     */
    private CacheFilter cacheFilter;

    /**
     * A filter used whenever an effect is placed on the node. Of course
     * effects can form a kind of tree, such that this one effect might be
     * an accumulation of several different effects. This will be null if
     * there are no effects on the FX scene graph node.
     */
    private EffectFilter effectFilter;

    /**
     * The effect that is applied to the "backdrop" of this node, that is, to the pixels that have
     * already been rendered behind this node on the current render target.
     * <p>
     * In contrast to {@link #effectFilter}, which operates on this node's own rendered contents,
     * the backdrop effect samples the content underneath the node, processes it through the
     * {@link Effect}, and composites the result as part of this node's content.
     * <p>
     * A value of {@code null} means that no backdrop effect is applied.
     */
    private Effect backdropEffect;

    /**
     * The number of this node's descendants with a backdrop effect, not including this node if it
     * has a backdrop effect. Since we need to keep track of backdrop-effect regions for dirty region
     * optimization, knowing whether there are any downstream backdrop-effect nodes can speed up
     * tree traversal by skipping subtrees without backdrop-effect nodes.
     */
    private int backdropEffectNodesWithin;

    /**
     * Flag indicating that the node is currently being rendered as a mask for a backdrop effect.
     * <p>
     * When rendering a backdrop effect, we first render this node into an off-screen mask image
     * which is later used to clip the filtered backdrop. During this mask pass, {@code backdropMask}
     * is set to {@code true} so that subclasses of this node can adjust their rendering behavior
     * to force a simple solid fill.
     * <p>
     * This flag is only valid during rendering and is set and cleared by
     * {@link #renderBackdropEffect(com.sun.prism.Graphics)}.
     */
    private boolean backdropMask;

    /**
     * If this node is an NGGroup, then this flag will be used to indicate
     * whether one or more of its children is dirty. While it would seem this
     * flag should be on NGGroup, the code turns out to be a bit cleaner with
     * this flag in the NGNode class.
     */
    protected boolean childDirty = false;

    /**
     * How many children are going to be accumulated
     */
    protected int dirtyChildrenAccumulated = 0;

    /**
     * Do not iterate over all children in group. Mark group as dirty
     * when threshold was reached.
     */
    protected final static int DIRTY_CHILDREN_ACCUMULATED_THRESHOLD = 12;

    /**
     * Marks position of this node in dirty regions.
     */
    protected int cullingBits = 0x0;
    private DirtyHint hint;

    /**
     * A cached representation of the opaque region for this node. This
     * cached version needs to be recomputed whenever the opaque region becomes
     * invalid, which includes local transform changes (translations included!).
     */
    private RectBounds opaqueRegion = null;

    /**
     * To avoid object churn we keep opaqueRegion around, and just toggle this
     * boolean to indicate whether we need to recompute the opaqueRegion.
     */
    private boolean opaqueRegionInvalid = true;

    /**
     * Used for debug purposes. This field will keep track of which nodes were
     * rendered as a result of different dirty regions. These correspond to the
     * same positions as the cullingBits. So for example, if a node was rendered
     * by dirty region 0, then painted will have the lowest bit set. If it
     * was rendered by dirty region 3, then it would have the 3rd bit from the
     * right set ( that is, 1 << 2)
     */
    private int painted = 0;

    protected NGNode() { }

    /***************************************************************************
     *                                                                         *
     *                Methods invoked during synchronization                   *
     *                                                                         *
     **************************************************************************/

    /**
     * Called by the FX scene graph to tell us whether we should be visible or not.
     * @param value whether it is visible
     */
    public void setVisible(boolean value) {
        // If the visibility changes, we need to mark this node as being dirty.
        // If this node is being cached, changing visibility should have no
        // effect, since it doesn't affect the rendering of the content in
        // any way. If we were to release the cached image, that might thwart
        // the developer's attempt to improve performance for things that
        // rapidly appear and disappear but which are expensive to render.
        // Ancestors, of course, must still have their caches invalidated.
        if (visible != value) {
            this.visible = value;
            markDirty();
        }
    }

    /**
     * Called by the FX scene graph to tell us what our new content bounds are.
     * @param bounds must not be null
     */
    public void setContentBounds(BaseBounds bounds) {
        // Note, there isn't anything to do here. We're dirty if geom or
        // visuals or transformed bounds or effects or clip have changed.
        // There's no point dealing with it here.
        contentBounds = contentBounds.deriveWithNewBounds(bounds);
    }

    /**
     * Called by the FX scene graph to tell us what our transformed bounds are.
     * @param bounds must not be null
     */
    public void setTransformedBounds(BaseBounds bounds, boolean byTransformChangeOnly) {
        if (transformedBounds.equals(bounds)) {
            // There has been no change, so ignore. It turns out this happens
            // a lot, because when a leaf has dirty bounds, all parents also
            // assume their bounds have changed, and only when they recompute
            // their bounds do we discover otherwise. This check could happen
            // on the FX side, however, then the FX side needs to cache the
            // former content bounds at the time of the last sync or needs to
            // be able to read state back from the NG side. Yuck. Just doing
            // it here for now.
            return;
        }
        // If the transformed bounds have changed, then we need to save off the
        // transformed bounds into the dirty bounds, so that the resulting
        // dirty region will be correct. If this node is cached, we DO NOT
        // invalidate the cache. The cacheFilter will compare its cached
        // transform to the accumulated transform to determine whether the
        // cache needs to be regenerated. So we will not invalidate it here.
        if (dirtyBounds.isEmpty()) {
            dirtyBounds = dirtyBounds.deriveWithNewBounds(transformedBounds);
        } else {
            // Non-empty dirty bounds mean that renderer did not consume them yet
            // (ex. because the Node was not yet visible). We need to unionize dirty
            // bounds with transformed bounds in case transformed bounds had changed
            // and then proceed with updating dirty bounds to their new value.
            dirtyBounds = dirtyBounds.deriveWithUnion(transformedBounds);
        }

        dirtyBounds = dirtyBounds.deriveWithUnion(bounds);
        transformedBounds = transformedBounds.deriveWithNewBounds(bounds);

        // Since the transformed bounds include the clip (and are thus changed when the
        // clip for this node has changed), this also invalidates the backdrop-effect
        // bounds for this node.
        backdropBoundsInvalid = true;

        if (hasVisuals() && !byTransformChangeOnly) {
            markDirty();
        }
    }

    /**
     * Called by the FX scene graph to tell us what our transform matrix is.
     * @param tx must not be null
     */
    public void setTransformMatrix(BaseTransform tx) {
        if (transform.equals(tx)) {
            return;
        }
        // If the transform matrix has changed, then we need to update it,
        // and mark this node as dirty. If this node is cached, we DO NOT
        // invalidate the cache. The cacheFilter will compare its cached
        // transform to the accumulated transform to determine whether the
        // cache needs to be regenerated. So we will not invalidate it here.
        // This approach allows the cached image to be reused in situations
        // where only the translation parameters of the accumulated transform
        // are changing. The scene will still be marked dirty and cached
        // images of any ancestors will be invalidated.
        boolean useHint = false;

        // If the parent is cached, try to check if the transformation is only a translation
        if (parent != null && parent.cacheFilter != null && PrismSettings.scrollCacheOpt) {
            if (hint == null) {
                // If there's no hint created yet, this is the first setTransformMatrix
                // call and we have nothing to compare to yet.
                hint = new DirtyHint();
            } else {
                if (transform.getMxx() == tx.getMxx()
                        && transform.getMxy() == tx.getMxy()
                        && transform.getMyy() == tx.getMyy()
                        && transform.getMyx() == tx.getMyx()
                        && transform.getMxz() == tx.getMxz()
                        && transform.getMyz() == tx.getMyz()
                        && transform.getMzx() == tx.getMzx()
                        && transform.getMzy() == tx.getMzy()
                        && transform.getMzz() == tx.getMzz()
                        && transform.getMzt() == tx.getMzt()) {
                    useHint = true;
                    hint.translateXDelta = tx.getMxt() - transform.getMxt();
                    hint.translateYDelta = tx.getMyt() - transform.getMyt();
                }
            }
        }

        transform = transform.deriveWithNewTransform(tx);
        if (useHint) {
            markDirtyByTranslation();
        } else {
            markDirty();
        }
        invalidateOpaqueRegion();
        backdropBoundsInvalid = true;
    }

    /**
     * Called by the FX scene graph whenever the clip node for this node changes.
     * @param clipNode can be null if the clip node is being cleared
     */
    public void setClipNode(NGNode clipNode) {
        // Whenever the clipNode itself has changed (that is, the reference to
        // the clipNode), we need to be sure to mark this node dirty and to
        // invalidate the cache of this node (if there is one) and all parents.
        if (clipNode != this.clipNode) {
            // Clear the "parent" property of the clip node, if there was one
            if (this.clipNode != null) this.clipNode.setParent(null);
            // Make the "parent" property of the clip node point to this
            if (clipNode != null) clipNode.setParent(this, true);
            // Keep the reference to the new clip node
            this.clipNode = clipNode;
            // Mark this node dirty, invalidate its cache, and all parents.
            visualsChanged();
            invalidateOpaqueRegion();
            backdropBoundsInvalid = true;
        }
    }

    /**
     * Called by the FX scene graph whenever the opacity for the node changes.
     * We create a special filter when the opacity is < 1.
     * @param opacity A value between 0 and 1.
     */
    public void setOpacity(float opacity) {
        // Check the argument to make sure it is valid.
        if (opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("Internal Error: The opacity must be between 0 and 1");
        }
        // If the opacity has changed, react. If this node is being cached,
        // then we do not want to invalidate the cache due to an opacity
        // change. However, as usual, all parent caches must be invalidated.
        if (opacity != this.opacity) {
            final float old = this.opacity;
            this.opacity = opacity;
            markDirty();
            // Even though the opacity has changed, for example from .5 to .6,
            // we don't need to invalidate the opaque region unless it has toggled
            // from 1 to !1, or from !1 to 1.
            if (old < 1 && (opacity == 1 || opacity == 0) || opacity < 1 && (old == 1 || old == 0)) {
                invalidateOpaqueRegion();
            }
        }
    }

    /**
     * Called by the FX scene graph whenever the view order for the node
     * changes.
     *
     * @param viewOrder A value between the range of negative Double.MAX_VALUE
     * and positive Double.MAX_VALUE.
     */
    public void setViewOrder(double viewOrder) {
        // If the viewOrder value has changed, react.
        if (viewOrder != this.viewOrder) {
            this.viewOrder = viewOrder;
            // Mark this node dirty and invalidate its cache.
            visualsChanged();
        }
    }

    /**
     * Set by the FX scene graph.
     * @param blendMode may be null to indicate "default"
     */
    public void setNodeBlendMode(Blend.Mode blendMode) {
        // The following code was a broken optimization that made an
        // incorrect assumption about null meaning the same thing as
        // SRC_OVER.  In reality, null means "pass through blending
        // from children" and SRC_OVER means "intercept blending of
        // children, allow them to blend with each other, but pass
        // their result on in a single SRC_OVER operation into the bg".
        // For leaf nodes, those are mostly the same thing, but Regions
        // and Groups might behave differently for the two modes.
//        if (blendMode == Blend.Mode.SRC_OVER) {
//            blendMode = null;
//        }

        // If the blend mode has changed, react. If this node is being cached,
        // then we do not want to invalidate the cache due to a compositing
        // change. However, as usual, all parent caches must be invalidated.

        if (this.nodeBlendMode != blendMode) {
            this.nodeBlendMode = blendMode;
            markDirty();
            invalidateOpaqueRegion();
        }
    }

    /**
     * Called by the FX scene graph whenever the derived depth test flag for
     * the node changes.
     * @param depthTest indicates whether to perform a depth test operation
     * (if the window has a depth buffer).
     */
    public void setDepthTest(boolean depthTest) {
        // If the depth test flag has changed, react.
        if (depthTest != this.depthTest) {
            this.depthTest = depthTest;
            // Mark this node dirty, invalidate its cache, and all parents.
            visualsChanged();
        }
    }

    /**
     * Called by the FX scene graph whenever "cached" or "cacheHint" changes.
     * These hints provide a way for the developer to indicate whether they
     * want this node to be cached as a raster, which can be quite a performance
     * optimization in some cases (and lethal in others).
     * @param cached specifies whether or not this node should be cached
     * @param cacheHint never null, indicates some hint as to how to cache
     */
    public void setCachedAsBitmap(boolean cached, CacheHint cacheHint) {
        // Validate the arguments
        if (cacheHint == null) {
            throw new IllegalArgumentException("Internal Error: cacheHint must not be null");
        }

        if (cached) {
            if (cacheFilter == null) {
                cacheFilter = new CacheFilter(this, cacheHint);
                // We do not technically need to do a render pass here, but if
                // we wait for the next render pass to cache it, then we will
                // cache not the current visuals, but the visuals as defined
                // by any transform changes that happen between now and then.
                // Repainting now encourages the cached version to be as close
                // as possible to the state of the node when the cache hint
                // was set...
                markDirty();
            } else {
                if (!cacheFilter.matchesHint(cacheHint)) {
                    cacheFilter.setHint(cacheHint);
                    // Different hints may have different requirements of
                    // whether the cache is stale.  We do not have enough info
                    // right here to evaluate that, but it will be determined
                    // naturally during a repaint cycle.
                    // If the new hint is more relaxed (QUALITY => SPEED for
                    // instance) then rendering should be quick.
                    // If the new hint is more restricted (SPEED => QUALITY)
                    // then we need to render to improve the results anyway.
                    markDirty();
                }
            }
        } else {
            if (cacheFilter != null) {
                cacheFilter.dispose();
                cacheFilter = null;
                // A cache will often look worse than uncached rendering.  It
                // may look the same in some circumstances, and this may then
                // be an unnecessary rendering pass, but we do not have enough
                // information here to be able to optimize that when possible.
                markDirty();
            }
        }
    }

    /**
     * Called by the FX scene graph to set the effect.
     * @param effect the effect (can be null to clear it)
     */
    public void setEffect(Effect effect) {
        final Effect old = getEffect();
        // When effects are disabled, be sure to reset the effect filter
        if (PrismSettings.disableEffects) {
            effect = null;
        }

        // We only need to take action if the effect is different than what was
        // set previously. There are four possibilities. Of these, #1 and #3 matter:
        // 0. effectFilter == null, effect == null
        // 1. effectFilter == null, effect != null
        // 2. effectFilter != null, effectFilter.effect == effect
        // 3. effectFilter != null, effectFilter.effect != effect
        // In any case where the effect is changed, we must both invalidate
        // the cache for this node (if there is one) and all parents, and mark
        // this node as dirty.
        if (effectFilter == null && effect != null) {
            effectFilter = new EffectFilter(effect, this);
            visualsChanged();
        } else if (effectFilter != null && effectFilter.getEffect() != effect) {
            effectFilter.dispose();
            effectFilter = null;
            if (effect != null) {
                effectFilter = new EffectFilter(effect, this);
            }
            visualsChanged();
        }

        // The only thing we do with the effect in #computeOpaqueRegion is to check
        // whether the effect is null / not null. If the answer to these question has
        // not changed from last time, then there is no need to recompute the opaque region.
        if (old != effect) {
            if (old == null || effect == null) {
                invalidateOpaqueRegion();
            }
        }
    }

    protected final Effect getEffect() {
        return effectFilter == null ? null : effectFilter.getEffect();
    }

    /**
     * Called by the FX scene graph when an effect in the effect chain on the node
     * changes internally.
     */
    public void effectChanged() {
        visualsChanged();
    }

    /**
     * Called by the FX scene graph to set the backdrop effect.
     * @param effect the effect (can be null to clear it)
     */
    public final void setBackdropEffect(Effect effect) {
        // When effects are disabled, be sure to reset the effect filter
        if (PrismSettings.disableEffects) {
            effect = null;
        }

        if (backdropEffect == effect) {
            return;
        }

        if (backdropEffect != null && effect == null) {
            NGNode parent = this.parent;
            while (parent != null) {
                parent.backdropEffectNodesWithin--;
                parent = parent.getParent();
            }
        } else if (backdropEffect == null && effect != null) {
            NGNode parent = this.parent;
            while (parent != null) {
                parent.backdropEffectNodesWithin++;
                parent = parent.getParent();
            }
        }

        Effect oldEffect = backdropEffect;
        backdropEffect = effect;
        backdropEffectChanged();
    }

    protected final Effect getBackdropEffect() {
        return backdropEffect;
    }

    /**
     * Called by the FX scene graph when a backdrop effect in the effect chain on the node
     * changes internally.
     */
    public final void backdropEffectChanged() {
        backdropBoundsInvalid = true;
        visualsChanged();
    }

    /**
     * Return true if contentBounds is purely a 2D bounds, ie. it is a
     * RectBounds or its Z dimension is almost zero.
     */
    public boolean isContentBounds2D() {
        return contentBounds.is2D();
    }

    /***************************************************************************
     *                                                                         *
     * Hierarchy, visibility, and other such miscellaneous NGNode properties   *
     *                                                                         *
     **************************************************************************/

    /**
     * Gets the parent of this node. The parent might be an NGGroup. However,
     * if this node is a clip node on some other node, then the node on which
     * it is set as the clip will be returned. That is, suppose some node A
     * has a clip node B. The method B.getParent() will return A.
     */
    public NGNode getParent() { return parent; }

    /**
     * Only called by this class, or by the NGGroup class.
     */
    public void setParent(NGNode parent) {
        int change = backdropEffectNodesWithin + (backdropEffect != null ? 1 : 0);
        if (change > 0) {
            NGNode p = this.parent;
            while (p != null) {
                p.backdropEffectNodesWithin -= change;
                p = p.getParent();
            }

            p = parent;
            while (p != null) {
                p.backdropEffectNodesWithin += change;
                p = p.getParent();
            }
        }

        setParent(parent, false);
    }

    private void setParent(NGNode parent, boolean isClip) {
        this.parent = parent;
        this.isClip = isClip;
    }

    /**
     * Used for debug purposes.
     */
    public final void setName(String value) {
        this.name = value;
    }

    /**
     * Used for debug purposes.
     */
    public final String getName() {
        return name;
    }

    /**
     * Gets whether this node's visible property is set
     */
    public boolean isVisible() { return visible; }

    public final BaseTransform getTransform() { return transform; }
    public final float getOpacity() { return opacity; }
    public final Blend.Mode getNodeBlendMode() { return nodeBlendMode; }
    public final boolean isDepthTest() { return depthTest; }
    public final CacheFilter getCacheFilter() { return cacheFilter; }
    public final EffectFilter getEffectFilter() { return effectFilter; }
    public final NGNode getClipNode() { return clipNode; }

    public BaseBounds getContentBounds(BaseBounds bounds, BaseTransform tx) {
        if (tx.isTranslateOrIdentity()) {
            bounds = bounds.deriveWithNewBounds(contentBounds);
            if (!tx.isIdentity()) {
                float translateX = (float) tx.getMxt();
                float translateY = (float) tx.getMyt();
                float translateZ = (float) tx.getMzt();
                bounds = bounds.deriveWithNewBounds(
                    bounds.getMinX() + translateX,
                    bounds.getMinY() + translateY,
                    bounds.getMinZ() + translateZ,
                    bounds.getMaxX() + translateX,
                    bounds.getMaxY() + translateY,
                    bounds.getMaxZ() + translateZ);
            }
            return bounds;
        } else {
            // This is a scale / rotate / skew transform.
            // We have contentBounds cached throughout the entire tree.
            // just walk down the tree and add everything up
            return computeBounds(bounds, tx);
        }
    }

    private BaseBounds computeBounds(BaseBounds bounds, BaseTransform tx) {
        // TODO: This code almost worked, but it ignored the local to
        // parent transforms on the nodes.  The short fix is to disable
        // this block and use the more general form below, but we need
        // to revisit this and see if we can make it work more optimally.
        // @see JDK-8091880
        if (false && this instanceof NGGroup) {
            List<NGNode> children = ((NGGroup)this).getChildren();
            BaseBounds tmp = TEMP_BOUNDS;
            for (int i=0; i<children.size(); i++) {
                float minX = bounds.getMinX();
                float minY = bounds.getMinY();
                float minZ = bounds.getMinZ();
                float maxX = bounds.getMaxX();
                float maxY = bounds.getMaxY();
                float maxZ = bounds.getMaxZ();
                NGNode child = children.get(i);
                bounds = child.computeBounds(bounds, tx);
                tmp = tmp.deriveWithNewBounds(minX, minY, minZ, maxX, maxY, maxZ);
                bounds = bounds.deriveWithUnion(tmp);
            }
            return bounds;
        } else {
            bounds = bounds.deriveWithNewBounds(contentBounds);
            return tx.transform(contentBounds, bounds);
        }
    }

    /**
     */
    public final BaseBounds getClippedBounds(BaseBounds bounds, BaseTransform tx) {
        BaseBounds effectBounds = getEffectBounds(bounds, tx);
        if (clipNode != null) {
            // there is a clip in place, so we will save off the effect/content
            // bounds (so as not to generate garbage) and will then get the
            // bounds of the clip node and do an intersection of the two
            float ex1 = effectBounds.getMinX();
            float ey1 = effectBounds.getMinY();
            float ez1 = effectBounds.getMinZ();
            float ex2 = effectBounds.getMaxX();
            float ey2 = effectBounds.getMaxY();
            float ez2 = effectBounds.getMaxZ();
            effectBounds = clipNode.getCompleteBounds(effectBounds, tx);
            effectBounds.intersectWith(ex1, ey1, ez1, ex2, ey2, ez2);
        }
        return effectBounds;
    }

    public final BaseBounds getEffectBounds(BaseBounds bounds, BaseTransform tx) {
        if (effectFilter != null) {
            return effectFilter.getBounds(bounds, tx);
        } else {
            return getContentBounds(bounds, tx);
        }
    }

    public final BaseBounds getCompleteBounds(BaseBounds bounds, BaseTransform tx) {
        if (tx.isIdentity()) {
            bounds = bounds.deriveWithNewBounds(transformedBounds);
            return bounds;
        } else if (transform.isIdentity()) {
            return getClippedBounds(bounds, tx);
        } else {
            double mxx = tx.getMxx();
            double mxy = tx.getMxy();
            double mxz = tx.getMxz();
            double mxt = tx.getMxt();
            double myx = tx.getMyx();
            double myy = tx.getMyy();
            double myz = tx.getMyz();
            double myt = tx.getMyt();
            double mzx = tx.getMzx();
            double mzy = tx.getMzy();
            double mzz = tx.getMzz();
            double mzt = tx.getMzt();
            BaseTransform boundsTx = tx.deriveWithConcatenation(this.transform);
            bounds = getClippedBounds(bounds, tx);
            if (boundsTx == tx) {
                tx.restoreTransform(mxx, mxy, mxz, mxt,
                                    myx, myy, myz, myt,
                                    mzx, mzy, mzz, mzt);
            }
            return bounds;
        }
    }

    private BaseBounds getBackdropBounds(BaseBounds bounds, BaseTransform tx) {
        if (backdropBoundsInvalid) {
            EffectDirtyBoundsHelper helper = EffectDirtyBoundsHelper.getInstance();
            helper.setInputBounds(getClippedBounds(TEMP_RECT_BOUNDS, BaseTransform.IDENTITY_TRANSFORM));
            BaseBounds effectBounds = backdropEffect.getBounds(BaseTransform.IDENTITY_TRANSFORM, helper);
            if (!transform.isIdentity()) {
                effectBounds = transform.transform(effectBounds, effectBounds);
            }

            backdropBounds = backdropBounds.deriveWithNewBounds(effectBounds);
            backdropBoundsInvalid = false;
        }

        return tx.isIdentity()
            ? bounds.deriveWithNewBounds(backdropBounds)
            : tx.transform(backdropBounds, bounds);
    }

    private RectBounds flattenBounds(RectBounds flatBounds, BaseBounds bounds, GeneralTransform3D tx) {
        if (!tx.isIdentity()) {
            bounds = tx.transform(bounds, bounds);
        }

        return bounds.flattenInto(flatBounds);
    }

    /***************************************************************************
     *                                                                         *
     * Dirty States                                                            *
     *                                                                         *
     **************************************************************************/

    /**
     * Invoked by subclasses whenever some change to the geometry or visuals
     * has occurred. This will mark the node as dirty and invalidate the cache.
     */
    protected void visualsChanged() {
        invalidateCache();
        markDirty();
    }

    protected void geometryChanged() {
        invalidateCache();
        invalidateOpaqueRegion();
        if (hasVisuals()) {
            markDirty();
        }
    }

    /**
     * Makes this node dirty, meaning that it needs to be included in the
     * next repaint to the back buffer, and its bounds should be included
     * in the dirty region. This flag means that this node itself is dirty.
     * In contrast, the childDirty flag indicates that a child of the node
     * (maybe a distant child) is dirty. This method does not invalidate the
     * cache of this node. However, it ends up walking up the tree marking
     * all parents as having a dirty child and also invalidating their caches.
     * This method has no effect if the node is already dirty.
     */
    public final void markDirty() {
        if (dirty != DirtyFlag.DIRTY) {
            dirty = DirtyFlag.DIRTY;
            markTreeDirty();
        }
    }

    /**
     * Mark the node as DIRTY_BY_TRANSLATION. This will call special cache invalidation
     */
    private void markDirtyByTranslation() {
        if (dirty == DirtyFlag.CLEAN) {
            if (parent != null && parent.dirty == DirtyFlag.CLEAN && !parent.childDirty) {
                dirty = DirtyFlag.DIRTY_BY_TRANSLATION;
                parent.childDirty = true;
                parent.dirtyChildrenAccumulated++;
                parent.invalidateCacheByTranslation(hint);
                parent.markTreeDirty();
            } else {
                markDirty();
            }
        }
    }

    //Mark tree dirty, but make sure this node's
    // dirtyChildrenAccumulated has not been incremented.
    // Useful when a markTree is called on a node that's not
    // the dirty source of change, e.g. group knows it has new child
    // or one of it's child has been removed
    protected final void markTreeDirtyNoIncrement() {
        if (parent != null && (!parent.childDirty || dirty == DirtyFlag.DIRTY_BY_TRANSLATION)) {
            markTreeDirty();
        }
    }

    /**
     * Notifies the parent (whether an NGGroup or just a NGNode) that
     * a child has become dirty. This walk will continue all the way up
     * to the root of the tree. If a node is encountered which is already
     * dirty, or which already has childDirty set, then this loop will
     * terminate (ie: there is no point going further so we might as well
     * just bail). This method ends up invalidating the cache of each
     * parent up the tree. Since it is possible for a node to already
     * have its dirty bit set, but not have its cache invalidated, this
     * method is careful to make sure the first parent it encounters
     * which is already marked dirty still has its cache invalidated. If
     * this turns out to be expensive due to high occurrence, we can add
     * a quick "invalidated" flag to every node (at the cost of yet
     * another bit).
     */
    protected final void markTreeDirty() {
        NGNode p = parent;
        boolean atClip = isClip;
        boolean byTranslation = dirty == DirtyFlag.DIRTY_BY_TRANSLATION;
        while (p != null && p.dirty != DirtyFlag.DIRTY && (!p.childDirty || atClip || byTranslation)) {
            if (atClip) {
                p.dirty = DirtyFlag.DIRTY;
            } else if (!byTranslation) {
                p.childDirty = true;
                p.dirtyChildrenAccumulated++;
            }
            p.invalidateCache();
            atClip = p.isClip;
            byTranslation = p.dirty == DirtyFlag.DIRTY_BY_TRANSLATION;
            p = p.parent;
        }
        // if we stopped on a parent that already has dirty children, increase it's
        // dirty children count.
        // Note that when incrementDirty is false, we don't increment in this case.
        if (p != null && p.dirty == DirtyFlag.CLEAN && !atClip && !byTranslation) {
            p.dirtyChildrenAccumulated++;
        }
        // Must make sure this happens. In some cases, a parent might
        // already be marked dirty (for example, its opacity may have
        // changed) but its cache has not been made invalid. This call
        // will make sure it is invalidated in that case
        if (p != null) p.invalidateCache();
    }

    /**
     * Gets whether this SGNode is clean. This will return true only if
     * this node and any / all child nodes are clean.
     */
    public final boolean isClean() {
        return dirty == DirtyFlag.CLEAN && !childDirty;
    }

    /**
     * Clears the dirty flag. This should only happen during rendering.
     */
    public void clearDirty() {
        if (dirty != DirtyFlag.CLEAN || childDirty) {
            dirty = DirtyFlag.CLEAN;
            childDirty = false;
            dirtyBounds.makeEmpty();
            dirtyChildrenAccumulated = 0;

            if (this instanceof NGGroup) {
                List<NGNode> children = ((NGGroup) this).getChildren();
                for (NGNode child : children) {
                    child.clearDirty();
                }
            }
        }
        if (getClipNode() != null) {
            getClipNode().clearDirty();
        }
    }

    /**
     * Walks down the tree clearing the "painted" bits for each node. This is only
     * called if we're drawing dirty rectangles or overdraw rectangles.
     */
    public void clearPainted() {
        painted = 0;
        if (this instanceof NGGroup) {
            List<NGNode> children = ((NGGroup)this).getChildren();
            for (int i=0; i<children.size(); i++) {
                children.get(i).clearPainted();
            }
        }
    }

    /**
     * Invalidates the cache, if it is in use. There are several operations
     * which need to cause the cached raster to become invalid so that a
     * subsequent render operation will result in the cached image being
     * reconstructed.
     */
    protected final void invalidateCache() {
        if (cacheFilter != null) {
            cacheFilter.invalidate();
        }
    }

    /**
     * Mark the cache as invalid due to a translation of a child. The cache filter
     * might use this information for optimizations.
     */
    protected final void invalidateCacheByTranslation(DirtyHint hint) {
        if (cacheFilter != null) {
            cacheFilter.invalidateByTranslation(hint.translateXDelta, hint.translateYDelta);
        }
    }

    /***************************************************************************
     *                                                                         *
     * Dirty Regions                                                           *
     *                                                                         *
     * Need to add documentation about dirty regions and how they work. One    *
     * thing to be aware of is that during the dirty region accumulation phase *
     * we use precise floating point values, but during                        *
     *                                                                         *
     **************************************************************************/

    /**
     * Accumulates dirty regions for this node and its subtree in transformed coordinates.
     * <p>
     * Dirty region accumulation is performed in render order and is designed so that a single downward
     * traversal of the scene graph is sufficient to determine the minimal set of regions that must be
     * repainted. Regions are accumulated only where they intersect the supplied {@code clip}, since
     * repainting outside the clip usually cannot affect the final image.
     * <p>
     * Backdrop effects add an additional dependency that must be tracked during accumulation.
     * A node with a backdrop effect samples pixels that were rendered behind it earlier in render order
     * (its backdrop input) and uses them to produce pixels in its own output bounds. This means:
     * <ul>
     *     <li>A node can need to be repainted even if it is otherwise {@linkplain DirtyFlag#CLEAN clean}
     *         if any previously recorded dirty region intersects its backdrop input bounds.
     *     <li>Even when a clean backdrop node does not become dirty in the current pass, its backdrop bounds
     *         are tracked as a potential dependency for backdrop nodes that come later in render order.
     *     <li>When repainting a region that includes pixels produced by backdrop-effect nodes, the repaint
     *         must sometimes be expanded to include additional pixels behind the region (padding), so that
     *         the effect sees up-to-date input pixels. This padding is computed by {@link BackdropRegionContainer}.
     * </ul>
     *
     * The {@code tx} parameter is the accumulated transform up to (but not including) this node's own transform.
     * If this method changes the transform instance, it must restore it before returning.
     *
     * @param clip clip in scene coordinates supplied by the rendering system, not {@code null}; typically this
     *             is the window content area, but it may be smaller (e.g., due to intermediate clipping)
     * @param dirtyRegionTemp scratch bounds used to compute dirty regions in scene coordinates, not {@code null}
     * @param regionPool pool for temporary {@link DirtyRegionContainer} instances, not {@code null}
     * @param dirtyRegionContainer accumulator for dirty regions in scene coordinates, not {@code null}
     * @param backdropRegionContainer tracker for render-order backdrop dependencies used to compute the
     *                                minimal repaint region, not {@code null}
     * @param tx accumulated transform up to but not including this node's transform, not {@code null}
     * @param pvTx perspective transform of the current camera, or identity for a parallel camera, not {@code null}
     * @return A status code indicating whether accumulation can continue normally ({@link DirtyRegionContainer#DTR_OK})
     *         or whether the dirty region has grown to cover the clip ({@link DirtyRegionContainer#DTR_CONTAINS_CLIP}),
     *         in which case we may repaint the entire clip/window as an optimization.
     *
     * TODO: Only made non-final for the sake of testing (see javafx-sg-prism tests) (JDK-8090845)
     */
    public /*final*/ int accumulateDirtyRegions(final RectBounds clip,
                                                final RectBounds dirtyRegionTemp,
                                                DirtyRegionPool regionPool,
                                                final DirtyRegionContainer dirtyRegionContainer,
                                                BackdropRegionPool backdropRegionPool,
                                                final BackdropRegionContainer backdropRegionContainer,
                                                final BaseTransform tx,
                                                final GeneralTransform3D pvTx) {
        // This is the main entry point, make sure to check these inputs for validity
        if (clip == null || dirtyRegionTemp == null || regionPool == null || dirtyRegionContainer == null ||
                tx == null || pvTx == null) throw new NullPointerException();

        // Even though a node with 0 visibility or 0 opacity doesn't get
        // rendered, it may contribute to the dirty bounds, for example, if it
        // WAS visible or if it HAD an opacity > 0 last time we rendered then
        // we must honor its dirty region. We have front-loaded this work so
        // that we don't mark nodes as having dirty flags or dirtyBounds if
        // they shouldn't contribute to the dirty region. So we can simply
        // treat all nodes, regardless of their opacity or visibility, as
        // though their dirty regions matter. They do.

        // Fast path: if this node and its subtree is clean, we can usually stop traversal here.
        // However, with backdrop effects, a clean node may still need to participate in dirty region
        // accumulation: If this node has a backdrop effect, it must either (a) become dirty because a
        // prior dirty region intersects its backdrop input, or (b) be tracked as a potential dependency
        // for later backdrop nodes.
        if (dirty == DirtyFlag.CLEAN && !childDirty && backdropEffectNodesWithin == 0) {
            if (backdropEffect != null) {
                accumulateBackdropDirtyRegion(dirtyRegionContainer, backdropRegionContainer, tx, pvTx);
            }

            return DirtyRegionContainer.DTR_OK;
        }

        int status = -1;

        // If this node is dirty itself, accumulate its dirty bounds (which may also introduce padding due to
        // earlier backdrop dependencies). Otherwise, if it is clean but has a backdrop effect, it may still
        // need to (a) extend an existing dirty region or (b) record its backdrop bounds for later queries.
        if (dirty != DirtyFlag.CLEAN) {
            status = accumulateNodeDirtyRegion(clip, dirtyRegionTemp, dirtyRegionContainer,
                                               backdropRegionContainer, tx, pvTx);
        } else if (backdropEffect != null) {
            accumulateBackdropDirtyRegion(dirtyRegionContainer, backdropRegionContainer, tx, pvTx);
        }

        // Only recurse into children when needed:
        // 1. if any child is dirty (normal dirty-region propagation)
        // 2. if the subtree contains backdrop-effect nodes, since their regions may need to be
        //    tracked or may be affected by dirty content recorded earlier in render order
        if ((childDirty || backdropEffectNodesWithin > 0)) {
            int groupStatus = accumulateGroupDirtyRegion(
                clip, dirtyRegionTemp, regionPool, dirtyRegionContainer,
                backdropRegionPool, backdropRegionContainer, tx, pvTx);

            if (status < 0 || groupStatus == DirtyRegionContainer.DTR_OK) {
                status = groupStatus;
            }
        }

        return status >= 0 ? status : DirtyRegionContainer.DTR_OK;
    }

    /**
     * Accumulates the dirty region of a node.
     * TODO: Only made non-final for the sake of testing (see javafx-sg-prism tests) (JDK-8090845)
     */
    int accumulateNodeDirtyRegion(final RectBounds clip,
                                  final RectBounds dirtyRegionTemp,
                                  final DirtyRegionContainer dirtyRegionContainer,
                                  final BackdropRegionContainer backdropRegionContainer,
                                  final BaseTransform tx,
                                  final GeneralTransform3D pvTx) {

        // Get the dirty bounds of this specific node in scene coordinates
        final BaseBounds bb = computeDirtyRegion(dirtyRegionTemp, tx, pvTx);

        // Note: dirtyRegion is strictly a 2D operation. We simply need the largest
        // rectangular bounds of bb. Hence the Z-axis projection of bb; taking
        // minX, minY, maxX and maxY values from this point on. Also, in many cases
        // bb == dirtyRegionTemp. In fact, the only time this won't be true is if
        // there is (or was) a perspective transform involved on this node.
        if (bb != dirtyRegionTemp) {
            bb.flattenInto(dirtyRegionTemp);
        }

        // If my dirty region is empty, or if it doesn't intersect with the
        // clip, then we can simply return since this node's dirty region is
        // not helpful
        if (dirtyRegionTemp.isEmpty() || clip.disjoint(dirtyRegionTemp)) {
            return DirtyRegionContainer.DTR_OK;
        }

        // If the clip is completely contained within the dirty region (including if they are equal)
        // then we return DTR_CONTAINS_CLIP, but only if neither this node nor any of its descendants
        // has a backdrop effect as we can't be sure that a backdrop effect doesn't sample pixels
        // outside of the clip.
        if (backdropEffectNodesWithin == 0 && backdropEffect == null && dirtyRegionTemp.contains(clip)) {
            return DirtyRegionContainer.DTR_CONTAINS_CLIP;
        }

        // The only overhead in calling intersectWith, and contains (above) is the repeated checking
        // if the isEmpty state. But the code is cleaner and less error prone.
        dirtyRegionTemp.intersectWith(clip);

        // If the opaque region of this node completely contains the dirty region, redrawing
        // this node within the dirty region never requires dependent backdrops to be redrawn.
        // We can therefore skip asking the backdrop dependency tracker whether there are any
        // backdrops under our dirty region.
        RectBounds opaqueRegion = getOpaqueRegion();
        if (opaqueRegion != null && opaqueRegion.contains(dirtyRegionTemp)) {
            dirtyRegionContainer.addDirtyRegion(dirtyRegionTemp);
            return DirtyRegionContainer.DTR_OK;
        }

        // If this node has a backdrop effect, the backdrop region is potentially larger than the
        // dirty region. We need to keep track of this excess by adding padding to the dirty region.
        if (backdropEffect != null) {
            BaseBounds backdropBounds = getBackdropBounds(TEMP_RECT_BOUNDS, tx);
            if (!pvTx.isIdentity()) {
                backdropBounds = pvTx.transform(backdropBounds, backdropBounds);
            }

            backdropBounds.flattenInto(TEMP_RECT_BOUNDS);
            TEMP_RECT_BOUNDS.unionWith(dirtyRegionTemp);
        } else {
            TEMP_RECT_BOUNDS.deriveWithNewBounds(dirtyRegionTemp);
        }

        backdropRegionContainer.computeRepaintRegion(TEMP_RECT_BOUNDS, TEMP_RECT_BOUNDS);

        dirtyRegionContainer.addDirtyRegion(
            dirtyRegionTemp,
            dirtyRegionTemp.getMinX() - TEMP_RECT_BOUNDS.getMinX(),
            dirtyRegionTemp.getMinY() - TEMP_RECT_BOUNDS.getMinY(),
            TEMP_RECT_BOUNDS.getMaxX() - dirtyRegionTemp.getMaxX(),
            TEMP_RECT_BOUNDS.getMaxY() - dirtyRegionTemp.getMaxY());

        return DirtyRegionContainer.DTR_OK;
    }

    /**
     * Accumulates the dirty region of an NGGroup. This is implemented here as opposed to
     * using polymorphism because we wanted to centralize all of the dirty region
     * management code in one place, rather than having it spread between Prism,
     * Scenario, and any other future toolkits.
     * TODO: Only made non-final for the sake of testing (see javafx-sg-prism tests) (JDK-8090845)
     */
    int accumulateGroupDirtyRegion(final RectBounds clip,
                                   final RectBounds dirtyRegionTemp,
                                   final DirtyRegionPool dirtyRegionPool,
                                   DirtyRegionContainer dirtyRegionContainer,
                                   final BackdropRegionPool backdropRegionPool,
                                   BackdropRegionContainer backdropRegionContainer,
                                   final BaseTransform tx,
                                   final GeneralTransform3D pvTx) {
        // If this group has exceeded the dirty children threshold, we mark the entire group as dirty
        // instead of traversing down to the individual children of this group. However, we can only do
        // this if neither this group nor any of its descendants has a backdrop effect, because backdrop
        // effects can sample distant pixels, which needs to be accounted for. Since we don't know how
        // large the extended dirty region of a backdrop effect can be, we need to visit all backdrop-
        // effect nodes and ask them how large their potential sampling region is.
        if (backdropEffectNodesWithin == 0 && backdropEffect == null
                && dirtyChildrenAccumulated > DIRTY_CHILDREN_ACCUMULATED_THRESHOLD) {
            return accumulateNodeDirtyRegion(clip, dirtyRegionTemp, dirtyRegionContainer,
                                             backdropRegionContainer, tx, pvTx);
        }

        int status = DirtyRegionContainer.DTR_OK;

        // If we got here, then we are following a "bread crumb" trail down to
        // some child (perhaps distant) which is dirty. So we need to iterate
        // over all the children and accumulate their dirty regions. Before doing
        // so we, will save off the transform state and restore it after having
        // called all the children.
        double mxx = tx.getMxx();
        double mxy = tx.getMxy();
        double mxz = tx.getMxz();
        double mxt = tx.getMxt();

        double myx = tx.getMyx();
        double myy = tx.getMyy();
        double myz = tx.getMyz();
        double myt = tx.getMyt();

        double mzx = tx.getMzx();
        double mzy = tx.getMzy();
        double mzz = tx.getMzz();
        double mzt = tx.getMzt();
        BaseTransform renderTx = tx;
        if (this.transform != null) renderTx = renderTx.deriveWithConcatenation(this.transform);

        // If this group node has a clip, then we will perform some special
        // logic which will cause the dirty region accumulation loops to run
        // faster. We already have a system whereby if a node determines that
        // its dirty region exceeds that of the clip, it simply returns null,
        // short circuiting the accumulation process. We extend that logic
        // here by also taking into account the clipNode on the group. If
        // there is a clip node, then we will union the bounds of the clip
        // node (in boundsInScene space) with the current clip and pass this
        // new clip down to the children. If they determine that their dirty
        // regions exceed the bounds of this new clip, then they will return
        // null. We'll catch that here, and use that information to know that
        // we ought to simply accumulate the bounds of this group as if it
        // were dirty. This process will do all the other optimizations we
        // already have in place for getting the normal dirty region.
        RectBounds myClip = clip;
        //Save current dirty region so we can fast-reset to (something like) the last state
        //and possibly save a few intersects() calls

        DirtyRegionContainer originalDirtyRegionContainer = null;
        BackdropRegionContainer originalBackdropRegionContainer = null;
        BaseTransform originalRenderTx = null;

        if (effectFilter != null) {
            BaseTransform invRenderTx = TEMP_TRANSFORM.deriveWithNewTransform(renderTx);

            try {
                invRenderTx.invert();
            } catch (NoninvertibleTransformException ex) {
                return DirtyRegionContainer.DTR_OK;
            }

            // Convert the current clip (scene space) into the effect-local coordinate frame.
            myClip = new RectBounds();
            BaseBounds myClipBaseBounds = invRenderTx.transform(clip, TEMP_BOUNDS);
            myClipBaseBounds.flattenInto(myClip);

            // Save the current traversal state and switch to effect-local traversal:
            // renderTx becomes identity so children report bounds relative to the effect node.
            originalRenderTx = renderTx;
            renderTx = BaseTransform.IDENTITY_TRANSFORM;

            // Check out local accumulation containers for the duration of the effect-local traversal.
            // DirtyRegionContainer:    children accumulate dirty regions in effect-local space
            // BackdropRegionContainer: children must see backdrop dependencies that were recorded
            //                          earlier in render order, but expressed in the same coordinate
            //                          space as their dirty regions.
            originalDirtyRegionContainer = dirtyRegionContainer;
            originalBackdropRegionContainer = backdropRegionContainer;
            dirtyRegionContainer = dirtyRegionPool.checkOut();

            // Children must be able to query backdrop dependencies recorded before we entered the effect.
            // Therefore, the local BackdropRegionContainer starts as a copy of the original BRC.
            // Then we transform that copied BRC into effect-local coordinates, so overlap tests and
            // dirty region expansion happen in the same coordinate frame that children will report.
            backdropRegionContainer = backdropRegionPool.checkOut();
            backdropRegionContainer.merge(originalBackdropRegionContainer, 0);
            backdropRegionContainer.transform(invRenderTx, 0);
        } else if (clipNode != null) {
            originalDirtyRegionContainer = dirtyRegionContainer;
            myClip = new RectBounds();
            flattenBounds(myClip, clipNode.getCompleteBounds(myClip, renderTx), pvTx);
            myClip.intersectWith(clip);
            dirtyRegionContainer = dirtyRegionPool.checkOut();
        }

        //Accumulate also removed children to dirty region.
        List<NGNode> removed = ((NGGroup) this).getRemovedChildren();
        if (removed != null) {
            NGNode removedChild;
            for (int i = removed.size() - 1; i >= 0; --i) {
                removedChild = removed.get(i);
                removedChild.dirty = DirtyFlag.DIRTY;
                int childStatus = removedChild.accumulateDirtyRegions(
                    myClip, dirtyRegionTemp, dirtyRegionPool, dirtyRegionContainer,
                    backdropRegionPool, backdropRegionContainer, renderTx, pvTx);

                if (childStatus == DirtyRegionContainer.DTR_CONTAINS_CLIP && backdropEffectNodesWithin == 0) {
                    status = DirtyRegionContainer.DTR_CONTAINS_CLIP;
                    break;
                }
            }
        }

        List<NGNode> children = ((NGGroup) this).getOrderedChildren();
        int num = children.size();
        for (int i=0; i<num && status == DirtyRegionContainer.DTR_OK; i++) {
            NGNode child = children.get(i);
            // The child will check the dirty bits itself. If we tested it here
            // (as we used to), we are just doing the check twice. True, it might
            // mean fewer method calls, but hotspot will probably inline this all
            // anyway, and doing the check in one place is less error prone.
            status = child.accumulateDirtyRegions(myClip, dirtyRegionTemp, dirtyRegionPool, dirtyRegionContainer,
                                                  backdropRegionPool, backdropRegionContainer, renderTx, pvTx);
            if (status == DirtyRegionContainer.DTR_CONTAINS_CLIP && backdropEffectNodesWithin == 0) {
                break;
            }
        }

        if (effectFilter != null && status == DirtyRegionContainer.DTR_OK) {
            applyEffect(effectFilter, dirtyRegionContainer, dirtyRegionPool);

            if (clipNode != null) {
                myClip = new RectBounds();
                BaseBounds clipBounds = clipNode.getCompleteBounds(myClip, renderTx);
                dirtyRegionContainer.intersectWith(clipBounds);
            }

            dirtyRegionContainer.transform(originalRenderTx);
            originalDirtyRegionContainer.merge(dirtyRegionContainer);
            dirtyRegionPool.checkIn(dirtyRegionContainer);

            // During the effect-local traversal, children may have queried backdropRegionContainer
            // (in effect-local coordinates) to compute padding, they may have consumed some entries
            // in the local backdropRegionContainer, and may have added new backdrop entries in
            // effect-local coordinates.
            //
            // We now need to bring those results back into the original BackdropRegionContainer:
            // 1. transform the effect-local BackdropRegionContainer back to scene space
            // 2. merge the state of the effect-local BackdropRegionContainer back into the original
            //    BackdropRegionContainer, which may add or remove existing entries
            //
            // The merge protocol of BackdropRegionContainer ensures that remaining entries in the
            // original BackdropRegionContainer (entries that were not removed in the effect-local
            // copy) are not changed due to floating-point errors caused by the back-and-forth
            // transformation between different coordinate spaces.
            int baseIndex = originalBackdropRegionContainer.entryCount();
            backdropRegionContainer.transform(originalRenderTx, baseIndex);
            originalBackdropRegionContainer.merge(backdropRegionContainer, baseIndex);
            backdropRegionPool.checkIn(backdropRegionContainer);

            renderTx = originalRenderTx;
        }

        // If the process of applying the transform caused renderTx to not equal
        // tx, then there is no point restoring it since it will be a different
        // reference and will therefore be gc'd.
        if (renderTx == tx) {
            tx.restoreTransform(mxx, mxy, mxz, mxt, myx, myy, myz, myt, mzx, mzy, mzz, mzt);
        }

        // If the dirty region is null and there is a clip node specified, then what
        // happened is that the dirty region of content within this group exceeded
        // the clip of this group, and thus, we should accumulate the bounds of
        // this group into the dirty region. If the bounds of the group exceeds
        // the bounds of the dirty region, then we end up returning null in the
        // end. But the implementation of accumulateNodeDirtyRegion handles this.
        if (clipNode != null && effectFilter == null) {
            if (status == DirtyRegionContainer.DTR_CONTAINS_CLIP) {
                status = accumulateNodeDirtyRegion(clip, dirtyRegionTemp, originalDirtyRegionContainer,
                                                   backdropRegionContainer, tx, pvTx);
            } else {
                originalDirtyRegionContainer.merge(dirtyRegionContainer);
            }

            dirtyRegionPool.checkIn(dirtyRegionContainer);

            if (originalBackdropRegionContainer != null) {
                backdropRegionPool.checkIn(originalBackdropRegionContainer);
            }
        }

        return status;
    }

    /**
     * Computes the dirty region for this Node. The specified region is in
     * scene coordinates. The specified tx can be used to convert local bounds
     * to scene bounds (it includes everything up to but not including my own
     * transform).
     *
     * @param dirtyRegionTemp A temporary RectBounds that this method can use for scratch.
     *                        In the case that no perspective transform occurs, it is best if
     *                        the returned BaseBounds is this instance.
     * @param tx Any transform that needs to be applied
     * @param pvTx must not be null, it's the perspective transform of the current
     *        perspective camera or identity transform if parallel camera is used.
     */
    private BaseBounds computeDirtyRegion(final RectBounds dirtyRegionTemp,
                                          final BaseTransform tx,
                                          final GeneralTransform3D pvTx)
    {
        if (cacheFilter != null) {
            return cacheFilter.computeDirtyBounds(dirtyRegionTemp, tx, pvTx);
        }
        // The passed in region is a scratch object that exists for me to use,
        // such that I don't have to create a temporary object. So I just
        // hijack it right here to start with. Note that any of the calls
        // in computeDirtyRegion might end up changing the region instance
        // from dirtyRegionTemp (which is a RectBounds) to a BoxBounds if any
        // of the other bounds / transforms involve a perspective transformation.
        BaseBounds region = dirtyRegionTemp;
        if (!dirtyBounds.isEmpty()) {
            region = region.deriveWithNewBounds(dirtyBounds);
        } else {
            // If dirtyBounds is empty, then we will simply set the bounds to
            // be the same as the transformedBounds (since that means the bounds
            // haven't changed and right now we don't support dirty sub regions
            // for generic nodes). This can happen if, for example, this is
            // a group with a clip and the dirty area of child nodes within
            // the group exceeds the bounds of the clip on the group. Just trust me.
            region = region.deriveWithNewBounds(transformedBounds);
        }

        // We shouldn't do anything with empty region, as we may accidentally make
        // it non empty or turn it into some nonsense (like (-1,-1,0,0) )
        if (!region.isEmpty()) {
                // Now that we have the dirty region, we will simply apply the tx
                // to it (after slightly padding it for good luck) to get the scene
                // coordinates for this.
                region = computePadding(region);
                region = tx.transform(region, region);
                region = pvTx.transform(region, region);
        }
        return region;
    }

    /**
     * Extends an existing dirty region to account for this node's backdrop effect, and tracks this node's
     * bounds in the {@link BackdropRegionContainer} to allow other nodes to discover backdrop dependencies
     * on this node.
     * <p>
     * In the render graph, dirty regions are accumulated in render order and normally only reflect pixels
     * that changed due to nodes repainting their own content. Backdrop effects break this assumption:
     * a backdrop-effect node samples pixels that were rendered <em>behind</em> it earlier in render order
     * and uses them to produce pixels in its own output area. Therefore, if any previously recorded dirty
     * region overlaps the area that this node may sample, then the output of this node can change even when
     * the node's own content did not.
     * <p>
     * This method checks whether any already-recorded dirty region intersects this node's backdrop input bounds.
     * If so, it extends the dirty region to account for this node, ensuring that the node will be repainted
     * in the next render pass so its backdrop output is recomputed from up-to-date pixels.
     * <p>
     * For backdrop effects that are not locally bounded (i.e. any input pixel can influence any output pixel),
     * any overlap between a dirty region and this node's input bounds forces a repaint of the node's full
     * output bounds. For locally-bounded effects (where an input pixel can only influence output pixels in
     * its neighborhood), the dependency is spatially limited: only the neighborhood of output pixels near
     * the dirty input needs to be repainted. In that case, this method:
     * <ol>
     *     <li>Computes the dirty-input sub-region that overlaps this node's backdrop input bounds.
     *     <li>Maps that dirty-input sub-region to the minimal output sub-region that can be affected, based
     *         on the effect's constant per-edge influence extents implied by its input-vs-output bounds.
     *     <li>Computes the minimal conservative repaint region needed to provide correct backdrop inputs
     *         for that output sub-region (including earlier backdrop-effect dependencies) via
     *         {@link BackdropRegionContainer#computeRepaintRegion(RectBounds, RectBounds)}.
     *     <li>Adds the node's dirty region with padding that captures the dependency on pixels behind it.
     * </ol>
     *
     * This method must only be called for nodes that have a backdrop effect.
     */
    private void accumulateBackdropDirtyRegion(DirtyRegionContainer dirtyRegionContainer,
                                               BackdropRegionContainer backdropRegionContainer,
                                               BaseTransform tx,
                                               GeneralTransform3D pvTx) {
        class Vars {
            final static RectBounds inputBounds = new RectBounds();
            final static RectBounds outputBounds = new RectBounds();
            final static RectBounds dirtyInputBounds = new RectBounds();
            final static RectBounds dirtyOutputBounds = new RectBounds();
            final static RectBounds requiredInputBounds = new RectBounds();
            final static RectBounds repaintBounds = new RectBounds();
        }

        RectBounds inputBounds = Vars.inputBounds;
        RectBounds outputBounds = Vars.outputBounds;
        RectBounds dirtyInputBounds = Vars.dirtyInputBounds;
        RectBounds dirtyOutputBounds = Vars.dirtyOutputBounds;
        RectBounds requiredInputBounds = Vars.requiredInputBounds;
        RectBounds repaintBounds = Vars.repaintBounds;
        boolean locallyBounded = backdropEffect.isInputLocal();
        boolean anyHit = false;

        // Backdrop input bounds for this node (the region the backdrop effect can sample)
        flattenBounds(inputBounds, getBackdropBounds(inputBounds, tx), pvTx);

        // We'll accumulate the union of the dirty pixels in inputBounds into dirtyInputBounds.
        // For backdrop effects that are not locally bounded, we can stop on the first intersection.
        dirtyInputBounds.makeEmpty();

        for (int i = 0, max = dirtyRegionContainer.size(); i < max; ++i) {
            RectBounds dirtyBounds = dirtyRegionContainer.getDirtyRegion(i);
            if (!dirtyBounds.intersects(inputBounds)) {
                continue;
            }

            anyHit = true;

            // If the backdrop effect is not locally bounded: any dirty input pixel can affect
            // any output pixel, so repaint the whole node.
            if (!locallyBounded) {
                break;
            }

            // If the backdrop effect is locally bounded: union the intersection into dirtyInputBounds
            float ix0 = Math.max(dirtyBounds.getMinX(), inputBounds.getMinX());
            float iy0 = Math.max(dirtyBounds.getMinY(), inputBounds.getMinY());
            float ix1 = Math.min(dirtyBounds.getMaxX(), inputBounds.getMaxX());
            float iy1 = Math.min(dirtyBounds.getMaxY(), inputBounds.getMaxY());
            if (ix1 >= ix0 && iy1 >= iy0) {
                dirtyInputBounds.unionWith(ix0, iy0, ix1, iy1);
            }
        }

        // Output bounds for this node (what it draws)
        flattenBounds(outputBounds, getCompleteBounds(outputBounds, tx), pvTx);

        // We didn't hit a dirty region, but the bounds of this backdrop might be relevant
        // for subsequent nodes, so track it in the backdrop region container.
        if (!anyHit) {
            backdropRegionContainer.add(outputBounds, inputBounds, backdropEffect.isInputLocal());
            return;
        }

        if (!locallyBounded || dirtyInputBounds.isEmpty()) {
            // Repaint whole node output, and its full input region.
            dirtyOutputBounds.setBounds(outputBounds);
            requiredInputBounds.setBounds(inputBounds);
        } else {
            // Compute per-edge extents from outputBounds to inputBounds.
            float left = outputBounds.getMinX() - inputBounds.getMinX();
            float top = outputBounds.getMinY() - inputBounds.getMinY();
            float right = inputBounds.getMaxX() - outputBounds.getMaxX();
            float bottom = inputBounds.getMaxY() - outputBounds.getMaxY();

            if (left < 0 || top < 0 || right < 0 || bottom < 0) {
                dirtyOutputBounds.setBounds(outputBounds);
                requiredInputBounds.setBounds(inputBounds);
            } else {
                // Map dirty input forward to affected output (inverse footprint).
                dirtyOutputBounds.setBounds(
                    Math.max(outputBounds.getMinX(), dirtyInputBounds.getMinX() - right),
                    Math.max(outputBounds.getMinY(), dirtyInputBounds.getMinY() - bottom),
                    Math.min(outputBounds.getMaxX(), dirtyInputBounds.getMaxX() + left),
                    Math.min(outputBounds.getMaxY(), dirtyInputBounds.getMaxY() + top));

                // Compute required input to repaint that output (forward footprint).
                requiredInputBounds.setBounds(
                    Math.max(inputBounds.getMinX(), dirtyOutputBounds.getMinX() - left),
                    Math.max(inputBounds.getMinY(), dirtyOutputBounds.getMinY() - top),
                    Math.min(inputBounds.getMaxX(), dirtyOutputBounds.getMaxX() + right),
                    Math.min(inputBounds.getMaxY(), dirtyOutputBounds.getMaxY() + bottom));
            }
        }

        // Compute the minimal repaint region for the required input bounds of this node.
        backdropRegionContainer.computeRepaintRegion(requiredInputBounds, repaintBounds);

        // Padding is the difference between the repaint bounds and this node's dirty output bounds:
        // the dirty output bounds define the area this node draws, and the repaint bounds define what
        // it needs to have up-to-date in order to do so.
        dirtyRegionContainer.addDirtyRegion(
            dirtyOutputBounds,
            dirtyOutputBounds.getMinX() - repaintBounds.getMinX(),
            dirtyOutputBounds.getMinY() - repaintBounds.getMinY(),
            repaintBounds.getMaxX() - dirtyOutputBounds.getMaxX(),
            repaintBounds.getMaxY() - dirtyOutputBounds.getMaxY());

        // If this node's output bounds are completely contained within the dirty output, the backdrop
        // of this node is irrelevant for subsequent queries because it will be repainted in any case.
        // Otherwise the backdrop might still be relevant for other nodes, so track it in the backdrop
        // region container.
        if (!dirtyOutputBounds.contains(outputBounds)) {
            backdropRegionContainer.add(outputBounds, inputBounds, backdropEffect.isInputLocal());
        }
    }

    /**
     * LCD Text creates some painful situations where, due to the LCD text
     * algorithm, we end up with some pixels touched that are normally outside
     * the bounds. To compensate, we need a hook for NGText to add padding.
     */
    protected BaseBounds computePadding(BaseBounds region) {
        return region;
    }

    /**
     * Marks if the node has some visuals and that the bounds change
     * should be taken into account when using the dirty region.
     * This will be false for NGGroup (but not for NGRegion)
     * @return true if the node has some visuals
     */
    protected boolean hasVisuals() {
        return true;
    }

    /***************************************************************************
     *                                                                         *
     * Culling                                                                 *
     *                                                                         *
     **************************************************************************/

    /**
     * Culling support for multiple dirty regions.
     * Set culling bits for the whole graph.
     * @param drc Array of dirty regions. Cannot be null.
     * @param tx The transform for this render operation. Cannot be null.
     * @param pvTx Perspective camera transformation. Cannot be null.
     */
    public final void doPreCulling(DirtyRegionContainer drc, BaseTransform tx, GeneralTransform3D pvTx) {
        if (drc == null || tx == null || pvTx == null) throw new NullPointerException();
        markCullRegions(drc, -1, tx, pvTx);
    }

    /**
     * Marks placement of the node in dirty region encoded into 2 bit flag:
     * 00 - node outside dirty region
     * 01 - node intersecting dirty region
     * 11 - node completely within dirty region
     *
     * 32 bits = 15 regions max. * 2 bit each. The first two bits are not used
     * because we have a special use case for -1, so they should only be set if
     * in that case.
     *
     * @param drc The array of dirty regions.
     * @param cullingRegionsBitsOfParent culling bits of parent. -1 if there's no parent.
     * @param tx The transform for this render operation. Cannot be null.
     * @param pvTx Perspective camera transform. Cannot be null.
     */
    void markCullRegions(
            DirtyRegionContainer drc,
            int cullingRegionsBitsOfParent,
            BaseTransform tx,
            GeneralTransform3D pvTx) {

        // Spent a long time tracking down how cullingRegionsBitsOfParent works. Note that it is
        // not just the parent's bits, but also -1 in the case of the "root", where the root is
        // either the actual root, or the root of a sub-render operation such as occurs with
        // render-to-texture for effects!

        if (tx.isIdentity()) {
            TEMP_BOUNDS.deriveWithNewBounds(transformedBounds);
        } else {
            tx.transform(transformedBounds, TEMP_BOUNDS);
        }

        if (!pvTx.isIdentity()) {
            pvTx.transform(TEMP_BOUNDS, TEMP_BOUNDS);
        }

        TEMP_BOUNDS.flattenInto(TEMP_RECT_BOUNDS);

        cullingBits = 0;
        RectBounds region;
        int mask = 0x1; // Check only for intersections
        for (int i = 0; i < drc.size(); i++) {
            // Check for intersections with the extended dirty region, not just the core region.
            // Even if the node is not visible within the render clip, its pixels might contribute
            // to another node's backdrop that is.
            region = drc.getEntry(i).getExtendedRegion();
            if (region == null || region.isEmpty()) {
                break;
            }

            // For each dirty region, we will check to see if this child
            // intersects with the dirty region and whether it contains the
            // dirty region. Note however, that we only care to mark those
            // child nodes which are inside a group that intersects. We don't
            // care about marking child nodes which are within a parent which
            // is wholly contained within the dirty region.
            if ((cullingRegionsBitsOfParent == -1 || (cullingRegionsBitsOfParent & mask) != 0) &&
                    region.intersects(TEMP_RECT_BOUNDS)) {
                int b = DIRTY_REGION_INTERSECTS_NODE_BOUNDS;
                if (region.contains(TEMP_RECT_BOUNDS)) {
                    b = DIRTY_REGION_CONTAINS_NODE_BOUNDS;
                }
                cullingBits = cullingBits | (b << (2 * i));
            }
            mask = mask << 2;
        }//for

//        System.out.printf("%s bits: %s bounds: %s\n",
//            this, Integer.toBinaryString(cullingBits), TEMP_RECT_BOUNDS);
    }

    /**
     * Fills the given StringBuilder with text representing the structure of the NG graph insofar as dirty
     * opts is concerned. Used for debug purposes. This is typically called on the root node. The List of
     * roots is the list of dirty roots as determined by successive calls to getRenderRoot for each dirty
     * region. The output will be prefixed with a key indicating how to interpret the printout.
     *
     * @param s A StringBuilder to fill with the output.
     * @param roots The list of render roots (may be empty, must not be null).
     */
    public final void printDirtyOpts(StringBuilder s, List<NGNode> roots) {
        s.append("\n*=Render Root\n");
        s.append("d=Dirty\n");
        s.append("dt=Dirty By Translation\n");
        s.append("i=Dirty Region Intersects the NGNode\n");
        s.append("c=Dirty Region Contains the NGNode\n");
        s.append("ef=Effect Filter\n");
        s.append("cf=Cache Filter\n");
        s.append("cl=This node is a clip node\n");
        s.append("b=Blend mode is set\n");
        s.append("or=Opaque Region\n");
        printDirtyOpts(s, this, BaseTransform.IDENTITY_TRANSFORM, "", roots);
    }

    /**
     * Used for debug purposes. Recursively visits all NGNodes and prints those that are possibly part of
     * the render operation and annotates each node.
     *
     * @param s The String builder
     * @param node The node that we're printing out information about
     * @param tx The transform
     * @param prefix Some prefix to put in front of the node output (mostly spacing)
     * @param roots The different dirty roots, if any.
     */
    private final void printDirtyOpts(StringBuilder s, NGNode node, BaseTransform tx, String prefix, List<NGNode> roots) {
        if (!node.isVisible() || node.getOpacity() == 0) return;

        BaseTransform copy = tx.copy();
        copy = copy.deriveWithConcatenation(node.getTransform());
        List<String> stuff = new ArrayList<>();
        for (int i=0; i<roots.size(); i++) {
            NGNode root = roots.get(i);
            if (node == root) stuff.add("*" + i);
        }

        if (node.dirty != NGNode.DirtyFlag.CLEAN) {
            stuff.add(node.dirty == NGNode.DirtyFlag.DIRTY ? "d" : "dt");
        }

        if (node.cullingBits != 0) {
            int mask = 0x11;
            for (int i=0; i<15; i++) {
                int bits = node.cullingBits & mask;
                if (bits != 0) {
                    stuff.add(bits == 1 ? "i" + i : bits == 0 ? "c" + i : "ci" + i);
                }
                mask = mask << 2;
            }
        }

        if (node.effectFilter != null) stuff.add("ef");
        if (node.cacheFilter != null) stuff.add("cf");
        if (node.nodeBlendMode != null) stuff.add("b");

        RectBounds opaqueRegion = node.getOpaqueRegion();
        if (opaqueRegion != null) {
            RectBounds or = new RectBounds();
            copy.transform(opaqueRegion, or);
            stuff.add("or=" + or.getMinX() + ", " + or.getMinY() + ", " + or.getWidth() + ", " + or.getHeight());
        }

        if (stuff.isEmpty()) {
            s.append(prefix + node.name + "\n");
        } else {
            String postfix = " [";
            for (int i=0; i<stuff.size(); i++) {
                postfix = postfix + stuff.get(i);
                if (i < stuff.size() - 1) postfix += " ";
            }
            s.append(prefix + node.name + postfix + "]\n");
        }

        if (node.getClipNode() != null) {
            printDirtyOpts(s, node.getClipNode(), copy, prefix + "  cl ", roots);
        }

        if (node instanceof NGGroup) {
            NGGroup g = (NGGroup)node;
            for (int i=0; i<g.getChildren().size(); i++) {
                printDirtyOpts(s, g.getChildren().get(i), copy, prefix + "  ", roots);
            }
        }
    }

    /**
     * Helper method draws rectangles indicating the overdraw rectangles.
     *
     * @param tx The scene->parent transform.
     * @param pvTx The perspective camera transform.
     * @param clipBounds The bounds in scene coordinates
     * @param colorBuffer A pixel array where each pixel contains a color indicating how many times
     *                    it has been "drawn"
     * @param dirtyRegionIndex the index of the dirty region we're gathering information for. This is
     *                         needed so we can shift the "painted" field to find out if this node
     *                         was drawn in this dirty region.
     */
    public void drawDirtyOpts(final BaseTransform tx, final GeneralTransform3D pvTx,
                              Rectangle clipBounds, int[] colorBuffer, int dirtyRegionIndex) {
        if ((painted & (1 << (dirtyRegionIndex * 2))) != 0) {
            // Transforming the content bounds (which includes the clip) to screen coordinates
            tx.copy().deriveWithConcatenation(getTransform()).transform(contentBounds, TEMP_BOUNDS);
            if (pvTx != null) pvTx.transform(TEMP_BOUNDS, TEMP_BOUNDS);
            RectBounds bounds = new RectBounds();
            TEMP_BOUNDS.flattenInto(bounds);

            // Adjust the bounds so that they are relative to the clip. The colorBuffer is sized
            // exactly the same as the clip, and the elements of the colorBuffer represent the
            // pixels inside the clip. However the bounds of this node may overlap the clip in
            // some manner, so we adjust them such that x, y, w, h will be the adjusted bounds.
            assert clipBounds.width * clipBounds.height == colorBuffer.length;
            bounds.intersectWith(clipBounds);
            int x = (int) bounds.getMinX() - clipBounds.x;
            int y = (int) bounds.getMinY() - clipBounds.y;
            int w = (int) (bounds.getWidth() + .5);
            int h = (int) (bounds.getHeight() + .5);

            if (w == 0 || h == 0) {
                // I would normally say we should never reach this point, as it means something was
                // marked as painted but really couldn't have been.
                return;
            }

            // x, y, w, h are 0 based and will fit within the clip, so now we can simply update
            // all the pixels that fall within these bounds.
            for (int i = y; i < y+h; i++) {
                for (int j = x; j < x+w; j++) {
                    final int index = i * clipBounds.width + j;
                    int color = colorBuffer[index];

                    // This is kind of a dirty hack. The idea is to show green if 0 or 1
                    // times a pixel is drawn, Yellow for 2 or 3 times, and red for more
                    // Than that. So I use 0x80007F00 as the first green color, and
                    // 0x80008000 as the second green color, but their so close to the same
                    // thing you probably won't be able to tell them apart, but I can tell
                    // numerically they're different and increment (so I use the colors
                    // as my counters).
                    if (color == 0) {
                        color = 0x8007F00;
                    } else if ((painted & (3 << (dirtyRegionIndex * 2))) == 3) {
                        switch (color) {
                            case 0x80007F00:
                                color = 0x80008000;
                                break;
                            case 0x80008000:
                                color = 0x807F7F00;
                                break;
                            case 0x807F7F00:
                                color = 0x80808000;
                                break;
                            case 0x80808000:
                                color = 0x807F0000;
                                break;
                            default:
                                color = 0x80800000;
                        }
                    }
                    colorBuffer[index] = color;
                }
            }
        }
    }

    /***************************************************************************
     *                                                                         *
     * Identifying render roots                                                *
     *                                                                         *
     **************************************************************************/
    protected static enum RenderRootResult {
        /**
         * A Node returns NO_RENDER_ROOT when it is not a render root because
         * it does not have an opaqueRegion which completely covers the area
         * of the clip. Maybe the node is dirty, but outside the dirty region
         * that we're currently processing. For an NGGroup, returning
         * NO_RENDER_ROOT means that there is no render root (occluder) within
         * this entire branch of the tree.
         */
        NO_RENDER_ROOT,
        /**
         * A Node returns HAS_RENDER_ROOT when its opaque region completely
         * covers the clip. An NGGroup returns HAS_RENDER_ROOT when one of
         * its children either returned HAS_RENDER_ROOT or HAS_RENDER_ROOT_AND_IS_CLEAN.
         */
        HAS_RENDER_ROOT,
        /**
         * A Node returns HAS_RENDER_ROOT_AND_IS_CLEAN when its opaque region
         * completely covers the clip and the Node is, itself, clean. An NGNode
         * returns HAS_RENDER_ROOT_AND_IS_CLEAN only if it had a child that
         * returned HAS_RENDER_ROOT_AND_IS_CLEAN and none of its children drawn
         * above the render root are dirty.
         *
         * This optimization allows us to recognize situations where perhaps there
         * were some dirty nodes, but they are completely covered by an occluder,
         * and therefore we don't actually have to draw anything.
         */
        HAS_RENDER_ROOT_AND_IS_CLEAN,
    }

    /**
     * Called <strong>after</strong> preCullingBits in order to get the node
     * from which we should begin drawing. This is our support for occlusion culling.
     * This should only be called on the root node.
     *
     * If no render root was found, we need to render everything from this root, so the path will contain this node.
     * If no rendering is needed (everything dirty is occluded), the path will remain empty
     *
     * @param path node path to store the node path
     */
    public final void getRenderRoot(NodePath path, RectBounds dirtyRegion, int cullingIndex,
                                    BaseTransform tx, GeneralTransform3D pvTx) {

        // This is the main entry point, make sure to check these inputs for validity
        if (path == null || dirtyRegion == null || tx == null || pvTx == null) {
            throw new NullPointerException();
        }
        if (cullingIndex < -1 || cullingIndex > 15) {
            throw new IllegalArgumentException("cullingIndex cannot be < -1 or > 15");
        }

        // This method must NEVER BE CALLED if the depth buffer is turned on. I don't have a good way to test
        // for that because NGNode doesn't have a reference to the scene it is a part of...

        RenderRootResult result = computeRenderRoot(path, dirtyRegion, cullingIndex, tx, pvTx);
        if (result == RenderRootResult.NO_RENDER_ROOT) {
            // We didn't find any render root, which means that no one node was large enough
            // to obscure the entire dirty region (or, possibly, some combination of nodes in an
            // NGGroup were not, together, large enough to do the job). So we need to render
            // from the root node, which is this node.
            path.add(this);
        } else if (result == RenderRootResult.HAS_RENDER_ROOT_AND_IS_CLEAN) {
            // We've found a render root, and it is clean and everything above it in painter order
            // is clean, so actually we have nothing to paint this time around (some stuff must
            // have been dirty which is completely occluded by the render root). So we can clear
            // the path, which indicates to the caller that nothing needs to be painted.
            path.clear();
        }
    }

    /**
     * Searches for the last node that covers all of the specified dirty region with an opaque region,
     * in this node's subtree. Such a node can serve as a rendering root as all nodes preceding the node
     * will be covered by it.
     *
     * @param path the NodePath to populate with the path to the render root. Cannot be null.
     * @param dirtyRegion the current dirty region. Cannot be null.
     * @param cullingIndex index of culling information
     * @param tx current transform. Cannot be null.
     * @param pvTx current perspective transform. Cannot be null.
     * @return The result of visiting this node.
     */
    RenderRootResult computeRenderRoot(NodePath path, RectBounds dirtyRegion,
                                       int cullingIndex, BaseTransform tx, GeneralTransform3D pvTx) {
        return computeNodeRenderRoot(path, dirtyRegion, cullingIndex, tx, pvTx);
    }

    private static Point2D[] TEMP_POINTS2D_4 =
            new Point2D[] { new Point2D(), new Point2D(), new Point2D(), new Point2D() };

    // Whether (px, py) is clockwise or counter-clockwise to a->b
    private static int ccw(double px, double py, Point2D a, Point2D b) {
        return (int)Math.signum(((b.x - a.x) * (py - a.y)) - (b.y - a.y) * (px - a.x));
    }

    private static boolean pointInConvexQuad(double x, double y, Point2D[] rect) {
        int ccw01 = ccw(x, y, rect[0], rect[1]);
        int ccw12 = ccw(x, y, rect[1], rect[2]);
        int ccw23 = ccw(x, y, rect[2], rect[3]);
        int ccw31 = ccw(x, y, rect[3], rect[0]);

        // Possible results after this operation:
        // 0 -> 0 (0x0)
        // 1 -> 1 (0x1)
        // -1 -> Integer.MIN_VALUE (0x80000000)
        ccw01 ^= (ccw01 >>> 1);
        ccw12 ^= (ccw12 >>> 1);
        ccw23 ^= (ccw23 >>> 1);
        ccw31 ^= (ccw31 >>> 1);

        final int union = ccw01 | ccw12 | ccw23 | ccw31;
        // This means all ccw* were either (-1 or 0) or (1 or 0), but not all of them were 0
        return union == 0x80000000 || union == 0x1;
        // Or alternatively...
//        return (union ^ (union << 31)) < 0;
    }

    /**
     * Check if this node can serve as rendering root for this dirty region.
     *
     * @param path the NodePath to populate with the path to the render root. Cannot be null.
     * @param dirtyRegion the current dirty region. Cannot be null.
     * @param cullingIndex index of culling information, -1 means culling information should not be used
     * @param tx current transform. Cannot be null.
     * @param pvTx current perspective transform. Cannot be null.
     * @return NO_RENDER_ROOT if this node does <em>not</em> have an opaque
     *         region that fills the entire dirty region. Returns HAS_RENDER_ROOT
     *         if the opaque region fills the dirty region.
     */
    final RenderRootResult computeNodeRenderRoot(NodePath path, RectBounds dirtyRegion,
                                                 int cullingIndex, BaseTransform tx, GeneralTransform3D pvTx) {

        // Nodes outside of the dirty region can be excluded immediately.
        // This can be used only if the culling information is provided.
        if (cullingIndex != -1) {
            final int bits = cullingBits >> (cullingIndex * 2);
            if ((bits & DIRTY_REGION_CONTAINS_OR_INTERSECTS_NODE_BOUNDS) == 0x00) {
                return RenderRootResult.NO_RENDER_ROOT;
            }
        }

        if (!isVisible()) {
            return RenderRootResult.NO_RENDER_ROOT;
        }

        final RectBounds opaqueRegion = getOpaqueRegion();
        if (opaqueRegion == null) return RenderRootResult.NO_RENDER_ROOT;

        final BaseTransform localToParentTx = getTransform();

        BaseTransform localToSceneTx = TEMP_TRANSFORM.deriveWithNewTransform(tx).deriveWithConcatenation(localToParentTx);

        // Now check if the dirty region is fully contained in our opaque region. Suppose the above
        // transform included a rotation about Z. In these cases, the transformed
        // opaqueRegion might be some non-axis aligned quad. So what we need to do is to check
        // that each corner of the dirty region lies within the (potentially rotated) quad
        // of the opaqueRegion.
        if (checkBoundsInQuad(opaqueRegion, dirtyRegion, localToSceneTx, pvTx)) {
            // This node is a render root.
            path.add(this);
            return isClean() ? RenderRootResult.HAS_RENDER_ROOT_AND_IS_CLEAN : RenderRootResult.HAS_RENDER_ROOT;
        }

        return RenderRootResult.NO_RENDER_ROOT;
    }

    static boolean checkBoundsInQuad(RectBounds untransformedQuad,
            RectBounds innerBounds, BaseTransform tx, GeneralTransform3D pvTx) {

        if (pvTx.isIdentity() && (tx.getType() & ~(BaseTransform.TYPE_TRANSLATION
                | BaseTransform.TYPE_QUADRANT_ROTATION
                | BaseTransform.TYPE_MASK_SCALE)) == 0) {
            // If pvTx is identity and there's simple transformation that will result in axis-aligned rectangle,
            // we can do a quick test by using bound.contains()
            if (tx.isIdentity()) {
                TEMP_BOUNDS.deriveWithNewBounds(untransformedQuad);
            } else {
                tx.transform(untransformedQuad, TEMP_BOUNDS);
            }

            TEMP_BOUNDS.flattenInto(TEMP_RECT_BOUNDS);

            return TEMP_RECT_BOUNDS.contains(innerBounds);
        } else {
            TEMP_POINTS2D_4[0].setLocation(untransformedQuad.getMinX(), untransformedQuad.getMinY());
            TEMP_POINTS2D_4[1].setLocation(untransformedQuad.getMaxX(), untransformedQuad.getMinY());
            TEMP_POINTS2D_4[2].setLocation(untransformedQuad.getMaxX(), untransformedQuad.getMaxY());
            TEMP_POINTS2D_4[3].setLocation(untransformedQuad.getMinX(), untransformedQuad.getMaxY());

            for (Point2D p : TEMP_POINTS2D_4) {
                tx.transform(p, p);
                if (!pvTx.isIdentity()) {
                    pvTx.transform(p, p);
                }
            }

            return (pointInConvexQuad(innerBounds.getMinX(), innerBounds.getMinY(), TEMP_POINTS2D_4)
                    && pointInConvexQuad(innerBounds.getMaxX(), innerBounds.getMinY(), TEMP_POINTS2D_4)
                    && pointInConvexQuad(innerBounds.getMaxX(), innerBounds.getMaxY(), TEMP_POINTS2D_4)
                    && pointInConvexQuad(innerBounds.getMinX(), innerBounds.getMaxY(), TEMP_POINTS2D_4));
        }
    }

    /**
     * Invalidates any cached representation of the opaque region for this node. On the next
     * call to getOpaqueRegion, the opaque region will be recalculated. Any changes to state
     * which is used in the {@link #hasOpaqueRegion()} call must invoke this method
     * or the opaque region calculations will be wrong.
     */
    protected final void invalidateOpaqueRegion() {
        opaqueRegionInvalid = true;
        if (isClip) parent.invalidateOpaqueRegion();
    }

    /**
     * This method exists only for the sake of testing.
     * @return value of opaqueRegionInvalid
     */
    final boolean isOpaqueRegionInvalid() {
        return opaqueRegionInvalid;
    }

    /**
     * Gets the opaque region for this node, if there is one, or returns null.
     * @return The opaque region for this node, or null.
     */
    public final RectBounds getOpaqueRegion() {
        // Note that when we invalidate the opaqueRegion of an NGNode, we don't
        // walk up the tree or communicate with the parents (unlike dirty flags).
        // An NGGroup does not compute an opaqueRegion based on the union of opaque
        // regions of its children (although this is a fine idea to consider!). See JDK-8092168
        // If we ever fix JDK-8092168, we must be sure to handle the case of a Group being used
        // as a clip node (such that invalidating a child on the group invalidates the
        // opaque region of every node up to the root).

        // Because the Effect classes have no reference to NGNode, they cannot tell the
        // NGNode to invalidate the opaque region whenever properties on the Effect that
        // would impact the opaqueRegion change. As a result, when an Effect is specified
        // on the NGNode, we will always treat it as if it were invalid. A more invasive
        // (but better) change would be to give Effect the ability to invalidate the
        // NGNode's opaque region when needed.
        if (opaqueRegionInvalid || getEffect() != null) {
            opaqueRegionInvalid = false;
            if (supportsOpaqueRegions() && hasOpaqueRegion()) {
                opaqueRegion = computeOpaqueRegion(opaqueRegion == null ? new RectBounds() : opaqueRegion);
                // If we got a null result then we encountered an error condition where somebody
                // claimed supportsOpaqueRegions and hasOpaqueRegion, but then they
                // returned null! This should never happen, so we have an assert here. However since
                // assertions are disabled at runtime and we want to avoid the NPE, we also perform
                // a null check.
                assert opaqueRegion != null;
                if (opaqueRegion == null) {
                    return null;
                }
                // If there is a clip, then we need to determine the opaque region of the clip, and
                // intersect that with our existing opaque region. For example, if I had a rectangle
                // with a circle for its clip (centered over the rectangle), then the result needs to
                // be the circle's opaque region.
                final NGNode clip = getClipNode();
                if (clip != null) {
                    final RectBounds clipOpaqueRegion = clip.getOpaqueRegion();
                    // Technically a flip/quadrant rotation is allowed as well, but we don't have a convenient
                    // way to do that yet.
                    if (clipOpaqueRegion == null || (clip.getTransform().getType() & ~(BaseTransform.TYPE_TRANSLATION | BaseTransform.TYPE_MASK_SCALE)) != 0) {
                        // JDK-8125859: If this node has a clip who's opaque region cannot be determined, then
                        // we cannot determine any opaque region for this node (in fact, it might not have one).
                        // Also, if the transform is something other than identity, scale, or translate then
                        // we're just going to bail (sorry, rotate, maybe next time!)
                        return opaqueRegion = null;
                    }
                    // We have to take into account any transform specified on the clip to put
                    // it into the same coordinate system as this node
                    final BaseBounds b = clip.getTransform().transform(clipOpaqueRegion, TEMP_BOUNDS);
                    b.flattenInto(TEMP_RECT_BOUNDS);
                    opaqueRegion.intersectWith(TEMP_RECT_BOUNDS);

                }
            } else {
                // The opaqueRegion may have been non-null in the past, but there isn't an opaque region now,
                // so we will nuke it to save some memory
                opaqueRegion = null;
            }
        }

        return opaqueRegion;
    }

    /**
     * Gets whether this NGNode supports opaque regions at all. Most node types do not,
     * but some do. If an NGNode subclass is written to support opaque regions, it must override
     * this method to return true. The subclass must then also override the computeDirtyRegion method
     * to return the dirty region, or null if the node in its current state doesn't have one.
     * This method is intended to be immutable.
     *
     * @return Whether this NGNode implementation supports opaque regions. This could also have been
     *         implemented via an interface that some NGNodes implemented, but then we'd have instanceof
     *         checks which I'd rather avoid.
     */
    protected boolean supportsOpaqueRegions() { return false; }

    /**
     * Called only on NGNode subclasses which override {@link #supportsOpaqueRegions()} to return
     * true, this method will return whether or not this NGNode is in a state where it has
     * an opaque region to actually return. If this method returns true, a subsequent call to
     * {@link #computeOpaqueRegion(com.sun.javafx.geom.RectBounds)} <strong>must</strong> return
     * a non-null result. Any state used in the computation of this method, when it changes, must
     * result in a call to {@link #invalidateOpaqueRegion()}.
     *
     * @return Whether this NGNode currently has an opaque region.
     */
    protected boolean hasOpaqueRegion() {
        final NGNode clip = getClipNode();
        final Effect effect = getEffect();
        return (effect == null || !effect.reducesOpaquePixels()) &&
               getOpacity() == 1f &&
               (nodeBlendMode == null || nodeBlendMode == Blend.Mode.SRC_OVER) &&
               (clip == null ||
               (clip.supportsOpaqueRegions() && clip.hasOpaqueRegion()));
    }

    /**
     * Computes and returns the opaque region for this node. This method
     * @param opaqueRegion
     * @return
     */
    protected RectBounds computeOpaqueRegion(RectBounds opaqueRegion) {
        return null;
    }

    /**
     * Returns whether a clip represented by this node can be rendered using
     * axis aligned rect clip. The default implementation returns false,
     * specific subclasses should override to return true when appropriate.
     *
     * @return whether this rectangle is axis aligned when rendered given node's
     * and rendering transform
     */
    protected boolean isRectClip(BaseTransform xform, boolean permitRoundedRectangle) {
        return false;
    }

    /***************************************************************************
     *                                                                         *
     * Rendering                                                               *
     *                                                                         *
     **************************************************************************/

    public final void render(Graphics g, Rectangle extendedClip) {
        if (extendedClip == null) {
            render(g);
        } else {
            BaseTransform tx = g.getTransformNoClone();
            double mxx = tx.getMxx(), mxy = tx.getMxy(), mxz = tx.getMxz(), mxt = tx.getMxt();
            double myx = tx.getMyx(), myy = tx.getMyy(), myz = tx.getMyz(), myt = tx.getMyt();
            double mzx = tx.getMzx(), mzy = tx.getMzy(), mzz = tx.getMzz(), mzt = tx.getMzt();

            FilterContext fctx = getFilterContext(g);
            PrDrawable img = null;

            try {
                img = (PrDrawable)Effect.getCompatibleImage(fctx, extendedClip.width, extendedClip.height);
                Graphics imgG = img.createGraphics();
                imgG.translate(-extendedClip.x, -extendedClip.y);
                imgG.transform(tx);
                imgG.setClipRect(new Rectangle(0, 0, extendedClip.width, extendedClip.height));
                render(imgG);
                g.setTransform(null);
                g.drawTexture(img.getTextureObject(),
                              extendedClip.x, extendedClip.y,
                              extendedClip.width, extendedClip.height);
            } finally {
                if (img != null) {
                    Effect.releaseCompatibleImage(fctx, img);
                }

                g.setTransform3D(mxx, mxy, mxz, mxt, myx, myy, myz, myt, mzx, mzy, mzz, mzt);
            }
        }
    }

    /**
     * Render the tree of nodes to the specified G (graphics) object
     * descending from this node as the root. This method is designed to avoid
     * generated trash as much as possible while descending through the
     * render graph while rendering. This is the appropriate method both to
     * initiate painting of an entire scene, and for a branch. The NGGroup
     * implementation must call this method on each child, not doRender directly.
     *
     * @param g The graphics object we're rendering to. This must never be null.
     */
    public final void render(Graphics g) {
        if (PULSE_LOGGING_ENABLED) {
            PulseLogger.incrementCounter("Nodes visited during render");
        }

        // If it isn't visible, then punt
        if (!visible || opacity == 0f) return;

        // We know that we are going to render this node, so we call the
        // doRender method, which subclasses implement to do the actual
        // rendering work.
        doRender(g);
    }

    /**
     * Called on every render pulse for all nodes in case they have render-time
     * operations that must be completed on a pulse, but were not otherwise
     * rendered by the ordinary damage management logic.
     * The graphics argument will be the graphics that was used to render the
     * scene if it is available, but may be null for cases when the scene
     * required no visible updates and thus no back buffer graphics was
     * actually obtained.  Implementors must have a backup plan for that
     * case when the Graphics object is null.
     *
     * @param gOptional the Graphics object that was used to render the
     *                  Scene, or null
     */
    public void renderForcedContent(Graphics gOptional) {
    }

    // This node requires 2D graphics state for rendering
    boolean isShape3D() {
        return false;
    }

    /**
     * Invoked only by the final render method. Implementations
     * of this method should make sure to save & restore the transform state.
     */
    protected void doRender(Graphics g) {

        g.setState3D(isShape3D());

        boolean preCullingTurnedOff = false;
        if (PrismSettings.dirtyOptsEnabled) {
            if (g.hasPreCullingBits()) {
                //preculling bits available
                final int bits = cullingBits >> (g.getClipRectIndex() * 2);
                if ((bits & DIRTY_REGION_CONTAINS_OR_INTERSECTS_NODE_BOUNDS) == 0) {
                    // If no culling bits are set for this region, this group
                    // does not intersect (nor is covered by) the region
                    return;
                } else if ((bits & DIRTY_REGION_CONTAINS_NODE_BOUNDS) != 0) {
                    // When this group is fully covered by the region,
                    // turn off the culling checks in the subtree, as everything
                    // gets rendered
                    g.setHasPreCullingBits(false);
                    preCullingTurnedOff = true;
                }
            }
        }

        // save current depth test state
        boolean prevDepthTest = g.isDepthTest();

        // Apply Depth test for this node
        // (note that this will only be used if we have a depth buffer for the
        // surface to which we are rendering)
        g.setDepthTest(isDepthTest());

        // save current transform state
        BaseTransform prevXform = g.getTransformNoClone();

        double mxx = prevXform.getMxx();
        double mxy = prevXform.getMxy();
        double mxz = prevXform.getMxz();
        double mxt = prevXform.getMxt();

        double myx = prevXform.getMyx();
        double myy = prevXform.getMyy();
        double myz = prevXform.getMyz();
        double myt = prevXform.getMyt();

        double mzx = prevXform.getMzx();
        double mzy = prevXform.getMzy();
        double mzz = prevXform.getMzz();
        double mzt = prevXform.getMzt();

        // filters are applied in the following order:
        //   transform
        //   blend mode
        //   opacity
        //   cache
        //   clip
        //   backdrop effect
        //   effect
        // The clip must be below the cache filter, as this is expected in the
        // CacheFilter in order to apply scrolling optimization
        g.transform(getTransform());
        // Try to keep track of whether this node was *really* painted. Still an
        // approximation, but somewhat more accurate (at least it doesn't include
        // groups which don't paint anything themselves).
        boolean p = false;
        // NOTE: Opt out 2D operations on 3D Shapes, which are not yet handled by Prism
        if (!isShape3D() && g instanceof ReadbackGraphics && needsBlending()) {
            renderNodeBlendMode(g);
            p = true;
        } else if (!isShape3D() && getOpacity() < 1f) {
            renderOpacity(g);
            p = true;
        } else if (!isShape3D() && getCacheFilter() != null) {
            renderCached(g);
            p = true;
        } else if (!isShape3D() && getClipNode() != null) {
            renderClip(g);
            p = true;
        } else if (!isShape3D() && getBackdropEffect() != null && effectsSupported) {
            renderBackdropEffect(g);
            p = true;
        } else if (!isShape3D() && getEffectFilter() != null && effectsSupported) {
            renderEffect(g);
            p = true;
        } else {
            renderContent(g);
            if (PrismSettings.showOverdraw) {
                p = this instanceof NGRegion || !(this instanceof NGGroup);
            }
        }

        if (preCullingTurnedOff) {
            g.setHasPreCullingBits(true);
        }

        // restore previous transform state
        g.setTransform3D(mxx, mxy, mxz, mxt,
                         myx, myy, myz, myt,
                         mzx, mzy, mzz, mzt);

        // restore previous depth test state
        g.setDepthTest(prevDepthTest);

        if (PULSE_LOGGING_ENABLED) {
            PulseLogger.incrementCounter("Nodes rendered");
        }

        // Used for debug purposes. This is not entirely accurate, as it doesn't measure the
        // number of times this node drew to the pixels, and in some cases reports a node as
        // having been drawn even when it didn't lay down any pixels. We'd need to integrate
        // with our shaders or do something much more invasive to get better data here.
        if (PrismSettings.showOverdraw) {
            if (p) {
                painted |= 3 << (g.getClipRectIndex() * 2);
            } else {
                painted |= 1 << (g.getClipRectIndex() * 2);
            }
        }
    }

    /**
     * Return true if this node has a blend mode that requires special
     * processing.
     * Regular nodes can handle null or SRC_OVER just by rendering into
     * the existing buffer.
     * Groups override this since they must collect their children into
     * a single rendering pass if their mode is explicitly SRC_OVER.
     * @return true if this node needs special blending support
     */
    protected boolean needsBlending() {
        Blend.Mode mode = getNodeBlendMode();
        return (mode != null && mode != Blend.Mode.SRC_OVER);
    }

    private void renderNodeBlendMode(Graphics g) {
        // The following is safe; curXform will not be mutated below
        BaseTransform curXform = g.getTransformNoClone();

        BaseBounds clipBounds = getClippedBounds(new RectBounds(), curXform);
        if (clipBounds.isEmpty()) {
            return;
        }

        if (!isReadbackSupported(g)) {
            if (getOpacity() < 1f) {
                renderOpacity(g);
            } else if (getClipNode() != null) {
                renderClip(g);
            } else {
                renderContent(g);
            }
            return;
        }

        // TODO: optimize this (JDK-8091917)
        // Extract clip bounds
        Rectangle clipRect = new Rectangle(clipBounds);
        clipRect.intersectWith(PrEffectHelper.getGraphicsClipNoClone(g));

        // render the node content into the first offscreen image
        FilterContext fctx = getFilterContext(g);
        PrDrawable contentImg = (PrDrawable)
            Effect.getCompatibleImage(fctx, clipRect.width, clipRect.height);
        if (contentImg == null) {
            return;
        }
        Graphics gContentImg = contentImg.createGraphics();
        gContentImg.setHasPreCullingBits(g.hasPreCullingBits());
        gContentImg.setClipRectIndex(g.getClipRectIndex());
        gContentImg.translate(-clipRect.x, -clipRect.y);
        gContentImg.transform(curXform);
        if (getOpacity() < 1f) {
            renderOpacity(gContentImg);
        } else if (getCacheFilter() != null) {
            renderCached(gContentImg);
        } else if (getClipNode() != null) {
            renderClip(g);
        } else if (getBackdropEffect() != null) {
            renderBackdropEffect(gContentImg);
        } else if (getEffectFilter() != null) {
            renderEffect(gContentImg);
        } else {
            renderContent(gContentImg);
        }

        // the above image has already been rendered in device space, so
        // just translate to the node origin in device space here...
        RTTexture bgRTT = ((ReadbackGraphics) g).readBack(clipRect);
        PrDrawable bgPrD = PrDrawable.create(fctx, bgRTT);
        Blend blend = new Blend(getNodeBlendMode(),
                                new PassThrough(bgPrD, clipRect),
                                new PassThrough(contentImg, clipRect));
        CompositeMode oldmode = g.getCompositeMode();
        g.setTransform(null);
        g.setCompositeMode(CompositeMode.SRC);
        PrEffectHelper.render(blend, g, 0, 0, null);
        g.setCompositeMode(oldmode);
        // transform state will be restored in render() method above...

        Effect.releaseCompatibleImage(fctx, contentImg);
        ((ReadbackGraphics) g).releaseReadBackBuffer(bgRTT);
    }

    private void renderRectClip(Graphics g, Rectangle clipRect) {
        if (clipRect.isEmpty()) {
            return;
        }
        final Rectangle curClip = g.getClipRectNoClone();
        // REMIND: avoid garbage by changing setClipRect to accept xywh
        g.setClipRect(clipRect);
        renderForClip(g);
        g.setClipRect(curClip);
    }

    void renderClip(Graphics g) {
        //  if clip's opacity is 0 there's nothing to render
        if (getClipNode().getOpacity() == 0.0) {
            return;
        }

        // The following is safe; curXform will not be mutated below
        BaseTransform curXform = g.getTransformNoClone();

        BaseBounds clipBounds = getClippedBounds(new RectBounds(), curXform);
        if (clipBounds.isEmpty()) {
            return;
        }

        // TODO: optimize this (JDK-8091917)
        // Extract clip bounds
        Rectangle clipRect = new Rectangle(clipBounds);
        clipRect.intersectWith(PrEffectHelper.getGraphicsClipNoClone(g));

        if (getClipNode() instanceof NGRectangle) {
            // optimized case for rectangular clip
            NGRectangle rectNode = (NGRectangle)getClipNode();
            if (rectNode.isRectClip(curXform, false)) {
                renderRectClip(g, clipRect);
                return;
            }
        }

        if (!curXform.is2D()) {
            Rectangle savedClip = g.getClipRect();
            g.setClipRect(clipRect);
            NodeEffectInput clipInput =
                new NodeEffectInput(getClipNode(),
                                    NodeEffectInput.RenderType.FULL_CONTENT);
            NodeEffectInput nodeInput =
                new NodeEffectInput(this,
                                    NodeEffectInput.RenderType.CLIPPED_CONTENT);
            Blend blend = new Blend(Blend.Mode.SRC_IN, clipInput, nodeInput);
            PrEffectHelper.render(blend, g, 0, 0, null);
            clipInput.flush();
            nodeInput.flush();
            g.setClipRect(savedClip);
            return;
        }

        // render the node content into the first offscreen image
        FilterContext fctx = getFilterContext(g);
        PrDrawable contentImg = (PrDrawable)
            Effect.getCompatibleImage(fctx, clipRect.width, clipRect.height);
        if (contentImg == null) {
            return;
        }
        Graphics gContentImg = contentImg.createGraphics();
        gContentImg.setExtraAlpha(g.getExtraAlpha());
        gContentImg.setHasPreCullingBits(g.hasPreCullingBits());
        gContentImg.setClipRectIndex(g.getClipRectIndex());
        gContentImg.translate(-clipRect.x, -clipRect.y);
        gContentImg.transform(curXform);
        renderForClip(gContentImg);

        // render the mask (clipNode) into the second offscreen image
        PrDrawable clipImg = (PrDrawable)
            Effect.getCompatibleImage(fctx, clipRect.width, clipRect.height);
        if (clipImg == null) {
            Effect.releaseCompatibleImage(fctx, contentImg);
            return;
        }
        Graphics gClipImg = clipImg.createGraphics();
        gClipImg.translate(-clipRect.x, -clipRect.y);
        gClipImg.transform(curXform);
        getClipNode().render(gClipImg);

        // the above images have already been rendered in device space, so
        // just translate to the node origin in device space here...
        g.setTransform(null);
        Blend blend = new Blend(Blend.Mode.SRC_IN,
                                new PassThrough(clipImg, clipRect),
                                new PassThrough(contentImg, clipRect));
        PrEffectHelper.render(blend, g, 0, 0, null);
        // transform state will be restored in render() method above...

        Effect.releaseCompatibleImage(fctx, contentImg);
        Effect.releaseCompatibleImage(fctx, clipImg);
    }

    void renderForClip(Graphics g) {
        if (getBackdropEffect() != null) {
            renderBackdropEffect(g);
        } else if (getEffectFilter() != null) {
            renderEffect(g);
        } else {
            renderContent(g);
        }
    }

    private void renderOpacity(Graphics g) {
        if (getEffectFilter() != null ||
            getCacheFilter() != null ||
            getClipNode() != null ||
            !hasOverlappingContents())
        {
            // if the node has a non-null effect or cached==true, we don't
            // need to bother rendering to an offscreen here because the
            // contents will be flattened as part of rendering the effect
            // (or creating the cached image)
            float ea = g.getExtraAlpha();
            g.setExtraAlpha(ea*getOpacity());
            if (getCacheFilter() != null) {
                renderCached(g);
            } else if (getClipNode() != null) {
                renderClip(g);
            } else if (getBackdropEffect() != null) {
                renderBackdropEffect(g);
            } else if (getEffectFilter() != null) {
                renderEffect(g);
            } else {
                renderContent(g);
            }
            g.setExtraAlpha(ea);
            return;
        }

        FilterContext fctx = getFilterContext(g);
        BaseTransform curXform = g.getTransformNoClone();
        BaseBounds bounds = getContentBounds(new RectBounds(), curXform);
        Rectangle r = new Rectangle(bounds);
        r.intersectWith(PrEffectHelper.getGraphicsClipNoClone(g));
        PrDrawable img = (PrDrawable)
            Effect.getCompatibleImage(fctx, r.width, r.height);
        if (img == null) {
            return;
        }
        Graphics gImg = img.createGraphics();
        gImg.setHasPreCullingBits(g.hasPreCullingBits());
        gImg.setClipRectIndex(g.getClipRectIndex());
        gImg.translate(-r.x, -r.y);
        gImg.transform(curXform);
        renderContent(gImg);
        // img contents have already been rendered in device space, so
        // just translate to the node origin in device space here...
        g.setTransform(null);
        float ea = g.getExtraAlpha();
        g.setExtraAlpha(getOpacity()*ea);
        g.drawTexture(img.getTextureObject(), r.x, r.y, r.width, r.height);
        g.setExtraAlpha(ea);
        // transform state will be restored in render() method above...
        Effect.releaseCompatibleImage(fctx, img);
    }

    private void renderCached(Graphics g) {
        // We will punt on 3D completely for caching.
        // The first check is for any of its children contains a 3D Transform.
        // The second check is for any of its parents and itself has a 3D Transform
        // The third check is for the printing case, which doesn't use cached
        // bitmaps for the screen and for which there is no cacheFilter.
        if (isContentBounds2D() && g.getTransformNoClone().is2D() &&
                !(g instanceof com.sun.prism.PrinterGraphics)) {
            getCacheFilter().render(g);
        } else {
            renderContent(g);
        }
    }

    protected void renderEffect(Graphics g) {
        getEffectFilter().render(g);
    }

    protected final void renderBackdropEffect(Graphics g) {
        if (!(g instanceof ReadbackGraphics rg) || !rg.canReadBack()) {
            if (getEffect() != null) {
                renderEffect(g);
            } else {
                renderContent(g);
            }

            return;
        }

        // Save the current transform so we can restore it later.
        BaseTransform tx = g.getTransformNoClone();
        double mxx = tx.getMxx(), mxy = tx.getMxy(), mxz = tx.getMxz(), mxt = tx.getMxt();
        double myx = tx.getMyx(), myy = tx.getMyy(), myz = tx.getMyz(), myt = tx.getMyt();
        double mzx = tx.getMzx(), mzy = tx.getMzy(), mzz = tx.getMzz(), mzt = tx.getMzt();

        // Compute the axis-aligned clip rectangle of the current node when viewed under the current
        // transform and the current graphics clip. This is the backdrop area that can potentially be
        // visible under the node.
        BaseBounds clippedBoundsNoTx = getClippedBounds(new RectBounds(), BaseTransform.IDENTITY_TRANSFORM);
        Rectangle clipRectTx = new Rectangle(tx.transform(clippedBoundsNoTx, TEMP_RECT_BOUNDS));
        clipRectTx.intersectWith(PrEffectHelper.getGraphicsClipNoClone(g));
        if (clipRectTx.isEmpty()) {
            return;
        }

        FilterContext fctx = getFilterContext(g);
        PrDrawable finalImg = null;
        PrDrawable maskImg = null;
        PrDrawable backdropImg = null;
        RTTexture backdropTex = null;

        try {
            // Render the node content as a white mask into an offscreen image.
            // This mask is used to clip the node's backdrop image later.
            maskImg = (PrDrawable)Effect.getCompatibleImage(fctx, clipRectTx.width, clipRectTx.height);
            if (maskImg == null) {
                return;
            }

            Graphics gMaskImg = maskImg.createGraphics();
            gMaskImg.translate(-clipRectTx.x, -clipRectTx.y);
            gMaskImg.transform(tx);
            backdropMask = true;
            if (getEffectFilter() != null) {
                renderEffect(gMaskImg);
            } else {
                renderContent(gMaskImg);
            }
            backdropMask = false;

            // Earlier, we've computed the backdrop area of the current node. We need to ask the backdrop
            // effect how that area changes when the effect is applied. Some effects can grow or shrink the
            // area to which they're applied; for example, a blur effect grows the area in every direction
            // by its blur radius.
            EffectDirtyBoundsHelper helper = EffectDirtyBoundsHelper.getInstance();
            helper.setInputBounds(clippedBoundsNoTx);
            BaseBounds backdropEffectBoundsNoTx = backdropEffect.getBounds(BaseTransform.IDENTITY_TRANSFORM, helper);
            Rectangle backdropRectTx = new Rectangle(tx.transform(backdropEffectBoundsNoTx, TEMP_RECT_BOUNDS));
            backdropImg = (PrDrawable)Effect.getCompatibleImage(fctx, backdropRectTx.width, backdropRectTx.height);
            if (backdropImg == null) {
                return;
            }

            // Now we need to read the region of the render target that is covered by the backdrop effect
            // area into an off-screen image. This image contains everything that has been rendered so far
            // under the current node.
            Surface rt = rg.getRenderTarget();
            TEMP_RECTANGLE.setBounds(rt.getContentX(), rt.getContentY(), rt.getContentWidth(), rt.getContentHeight());
            TEMP_RECTANGLE.intersectWith(backdropRectTx);
            backdropTex = rg.readBack(TEMP_RECTANGLE);

            // Apply the backdrop effect to the image we read from the render target.
            Graphics gBackdropImg = backdropImg.createGraphics();
            TEMP_RECTANGLE.setBounds(
                TEMP_RECTANGLE.x - backdropRectTx.x, TEMP_RECTANGLE.y - backdropRectTx.y,
                TEMP_RECTANGLE.width, TEMP_RECTANGLE.height);
            PrEffectHelper.render(
                backdropEffect, gBackdropImg, 0, 0,
                new PassThrough(PrDrawable.create(fctx, backdropTex), TEMP_RECTANGLE));

            // We now have two images: the backdrop image with its effect applied, and the mask image
            // of the node content. We use a blend effect to clip the backdrop image with the mask, and
            // render the result back to the render target.
            g.setTransform(null); // switch back to device space (null -> identity)
            PrEffectHelper.render(
                new Blend(
                    Blend.Mode.SRC_IN,
                    new PassThrough(maskImg, clipRectTx),
                    new PassThrough(backdropImg, backdropRectTx)),
                g, 0, 0, null);
        } finally {
            if (backdropTex != null) {
                rg.releaseReadBackBuffer(backdropTex);
            }

            if (backdropImg != null) {
                Effect.releaseCompatibleImage(fctx, backdropImg);
            }

            if (maskImg != null) {
                Effect.releaseCompatibleImage(fctx, maskImg);
            }

            if (finalImg != null) {
                Effect.releaseCompatibleImage(fctx, finalImg);
            }
        }

        g.setTransform3D(mxx, mxy, mxz, mxt, myx, myy, myz, myt, mzx, mzy, mzz, mzt);

        // After we have rendered the backdrop of the node, we render its content on top.
        if (getEffect() != null) {
            renderEffect(g);
        } else {
            renderContent(g);
        }
    }

    protected abstract void renderContent(Graphics g);

    protected abstract boolean hasOverlappingContents();

    protected final boolean isBackdropMask() {
        return backdropMask;
    }

    /***************************************************************************
     *                                                                         *
     *                       Static Helper Methods.                            *
     *                                                                         *
     **************************************************************************/

    boolean isReadbackSupported(Graphics g) {
        return ((g instanceof ReadbackGraphics) &&
                ((ReadbackGraphics) g).canReadBack());
    }

    /***************************************************************************
     *                                                                         *
     *                      Filters (Cache, Effect, etc).                      *
     *                                                                         *
     **************************************************************************/

    static FilterContext getFilterContext(Graphics g) {
        Screen s = g.getAssociatedScreen();
        if (s == null) {
            return PrFilterContext.getPrinterContext(g.getResourceFactory());
        } else {
            return PrFilterContext.getInstance(s);
        }
    }

    /**
     * A custom effect implementation that has a filter() method that
     * simply wraps the given pre-rendered PrDrawable in an ImageData
     * and returns that result.  This is only used by the renderClip()
     * implementation so we cut some corners here (for example, we assume
     * that the given PrDrawable image is already in device space).
     */
    private static class PassThrough extends Effect {
        private PrDrawable img;
        private Rectangle bounds;

        PassThrough(PrDrawable img, Rectangle bounds) {
            this.img = img;
            this.bounds = bounds;
        }

        @Override
        public ImageData filter(FilterContext fctx,
                                BaseTransform transform,
                                Rectangle outputClip,
                                Object renderHelper,
                                Effect defaultInput)
        {
            img.lock();
            ImageData id = new ImageData(fctx, img, new Rectangle(bounds));
            id.setReusable(true);
            return id;
        }

        @Override
        public RectBounds getBounds(BaseTransform transform,
                                  Effect defaultInput)
        {
            return new RectBounds(bounds);
        }

        @Override
        public AccelType getAccelType(FilterContext fctx) {
            return AccelType.INTRINSIC;
        }

        @Override
        public boolean reducesOpaquePixels() {
            return false;
        }

        @Override
        public DirtyRegionContainer getDirtyRegions(Effect defaultInput, DirtyRegionPool regionPool) {
            return null; //Never called
        }
    }

    /***************************************************************************
     *                                                                         *
     * Stuff                                                                   *
     *                                                                         *
     **************************************************************************/

    public void release() {
    }

    @Override public String toString() {
        return name == null ? super.toString() : name;
    }

    public void applyEffect(final EffectFilter effectFilter, DirtyRegionContainer drc, DirtyRegionPool regionPool) {
        Effect effect = effectFilter.getEffect();
        EffectDirtyBoundsHelper helper = EffectDirtyBoundsHelper.getInstance();
        helper.setInputBounds(contentBounds);
        helper.setDirtyRegions(drc);
        final DirtyRegionContainer effectDrc = effect.getDirtyRegions(helper, regionPool);
        drc.deriveWithNewContainer(effectDrc);
        regionPool.checkIn(effectDrc);
    }

    private static class EffectDirtyBoundsHelper extends Effect {
        private BaseBounds bounds;
        private static EffectDirtyBoundsHelper instance = null;
        private DirtyRegionContainer drc;

        public void setInputBounds(BaseBounds inputBounds) {
            bounds = inputBounds;
        }

        @Override
        public ImageData filter(FilterContext fctx,
                BaseTransform transform,
                Rectangle outputClip,
                Object renderHelper,
                Effect defaultInput) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BaseBounds getBounds(BaseTransform transform, Effect defaultInput) {
            if (bounds.getBoundsType() == BaseBounds.BoundsType.RECTANGLE) {
                return bounds;
            } else {
                //JDK-8124495 - CCE: in case we get 3D bounds we need to "flatten" them
                return new RectBounds(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
            }
        }

        @Override
        public Effect.AccelType getAccelType(FilterContext fctx) {
            return null;
        }

        public static EffectDirtyBoundsHelper getInstance() {
            if (instance == null) {
                instance = new EffectDirtyBoundsHelper();
            }
            return instance;
        }

        @Override
        public boolean reducesOpaquePixels() {
            return true;
        }

        private void setDirtyRegions(DirtyRegionContainer drc) {
            this.drc = drc;
        }

        @Override
        public DirtyRegionContainer getDirtyRegions(Effect defaultInput, DirtyRegionPool regionPool) {
            DirtyRegionContainer ret = regionPool.checkOut();
            ret.deriveWithNewContainer(drc);

            return ret;
        }

    }
}
