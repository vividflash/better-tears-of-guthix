/*
 * Copyright (c) 2026, vividflash
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.vividflash.efficienttearsofguthix;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Marks the wall the rule picked, the next best one, and where the waiting
 * blue streams can and cannot land. What to mark is worked out once per game
 * tick; a frame only projects it onto the canvas.
 */
class TargetOverlay extends Overlay
{
    private static final Stroke STROKE = new BasicStroke(2);
    private static final Stroke DIM_STROKE = new BasicStroke(1);
    private static final int OUTLINE_WIDTH = 2;
    private static final int DIM_OUTLINE_WIDTH = 1;
    private static final int OUTLINE_FEATHER = 0;

    private static final Font TICKS_FONT = FontManager.getRunescapeBoldFont().deriveFont(32f);
    private static final Color TICKS_COLOR = Color.WHITE;
    private static final Color TICKS_ENDING_COLOR = new Color(255, 140, 0);
    private static final int TICKS_ENDING = 3;
    private static final Color DRY_TICKS_COLOR = Color.RED;
    private static final int TICKS_HEIGHT = 120;

    /** One wall to mark. A dim mark is a thin outline without a fill. */
    private static final class Mark
    {
        final int wall;
        final boolean tile;
        final boolean model;
        /** The object on the wall has a model to outline. */
        final boolean outline;
        final Color colour;
        final boolean dim;
        /** The object whose hull is drawn when there is no model to outline, found at rebuild. */
        final TileObject hullOwner;

        Mark(int wall, HighlightMode mode, Color colour, boolean dim, boolean outline, TileObject hullOwner)
        {
            this.hullOwner = hullOwner;
            this.wall = wall;
            this.tile = mode != HighlightMode.WALL;
            this.model = mode != HighlightMode.TILE;
            this.outline = outline;
            this.colour = colour;
            this.dim = dim;
        }
    }

    private final Client client;
    private final EfficientTearsOfGuthixConfig config;
    private final StreamAdvisor advisor;
    private final ModelOutlineRenderer outlineRenderer;

    /** The advisor revision the fields below were built from. */
    private int builtRevision = -1;
    private final List<Mark> marks = new ArrayList<>();
    private final LocalPoint[] wallPoints = new LocalPoint[TogLayout.WALLS];
    private final LocalPoint[] collectPoints = new LocalPoint[TogLayout.WALLS];
    /** Wall to draw the stream countdown on, or -1. */
    private int ticksWall = -1;
    private String ticksText;
    private Color ticksColour;

    @Inject
    TargetOverlay(Client client, EfficientTearsOfGuthixConfig config, StreamAdvisor advisor, ModelOutlineRenderer outlineRenderer)
    {
        this.client = client;
        this.config = config;
        this.advisor = advisor;
        this.outlineRenderer = outlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            if (!advisor.inArea() || !advisor.onPlane())
            {
                return null;
            }

            int revision = advisor.revision();
            if (revision != builtRevision)
            {
                builtRevision = revision;
                rebuild();
            }

            for (int i = 0; i < marks.size(); i++)
            {
                Mark mark = marks.get(i);
                if (mark.tile)
                {
                    draw(graphics, tilePoly(collectPoints[mark.wall]), mark.colour, mark.dim);
                }
                if (mark.model)
                {
                    markWall(graphics, mark);
                }
            }

            if (ticksWall >= 0)
            {
                streamTicks(graphics);
            }
        }
        catch (Exception e)
        {
            advisor.report("targetOverlay", e);
        }
        return null;
    }

    /** Once per game tick: which walls to mark in which colour, and the countdown text. */
    private void rebuild()
    {
        marks.clear();
        ticksWall = -1;
        Arrays.fill(wallPoints, null);
        Arrays.fill(collectPoints, null);
        if (!advisor.isSynced())
        {
            return;
        }

        WorldView view = client.getTopLevelWorldView();
        HighlightMode mode = config.highlightMode();
        if (view == null || mode == null)
        {
            return;
        }

        for (int wall = 0; wall < TogLayout.WALLS; wall++)
        {
            wallPoints[wall] = LocalPoint.fromWorld(view, TogLayout.wallX(wall), TogLayout.wallY(wall));
            collectPoints[wall] = LocalPoint.fromWorld(view, TogLayout.collectX(wall), TogLayout.collectY(wall));
        }

        int wall = advisor.targetWall();
        int second = advisor.secondWall();
        int[] wallMarks = advisor.wallMarks();
        boolean move = advisor.shouldMove();
        boolean possible = config.showPossible();
        boolean bad = config.showBad();
        for (int other = 0; other < TogLayout.WALLS; other++)
        {
            if (move && (other == wall || other == second))
            {
                continue;
            }
            if (bad && wallMarks[other] == TargetRule.MARK_BAD)
            {
                add(other, HighlightMode.TILE, config.badMarkColor(), true);
            }
            else if (possible && wallMarks[other] == TargetRule.MARK_POSSIBLE)
            {
                add(other, mode, config.possibleMarkColor(), true);
            }
        }

        if (move)
        {
            if (second >= 0)
            {
                add(second, mode, config.secondColor(), false);
            }
            if (wall >= 0)
            {
                add(wall, mode, config.bestColor(), false);
            }
        }

        int own = advisor.ownWall();
        int ticks = advisor.ownTicks();
        if (config.showStreamTicks() && own >= 0 && ticks > 0)
        {
            ticksWall = own;
            ticksText = Integer.toString(ticks);
            ticksColour = ticks <= TICKS_ENDING ? TICKS_ENDING_COLOR : TICKS_COLOR;
        }
        else if (own >= 0 && advisor.dryTicks() > 0)
        {
            ticksWall = own;
            ticksText = Integer.toString(advisor.dryTicks());
            ticksColour = DRY_TICKS_COLOR;
        }
    }

    private void add(int wall, HighlightMode mode, Color colour, boolean dim)
    {
        if (colour != null)
        {
            boolean outline = mode != HighlightMode.TILE && hasModel(wall);
            TileObject hullOwner = mode != HighlightMode.TILE && !outline ? hullOwner(advisor.wallTile(wall)) : null;
            marks.add(new Mark(wall, mode, colour, dim, outline, hullOwner));
        }
    }

    /** The first object on the tile that has a convex hull right now: a game object, else the wall object. */
    private static TileObject hullOwner(Tile tile)
    {
        if (tile == null)
        {
            return null;
        }

        for (GameObject object : tile.getGameObjects())
        {
            if (object != null && object.getConvexHull() != null)
            {
                return object;
            }
        }
        WallObject wallObject = tile.getWallObject();
        return wallObject != null && wallObject.getConvexHull() != null ? wallObject : null;
    }

    /** The convex hull of a game or wall object, the two kinds {@link #hullOwner} returns. */
    private static Shape hull(TileObject object)
    {
        if (object instanceof GameObject)
        {
            return ((GameObject) object).getConvexHull();
        }
        return object instanceof WallObject ? ((WallObject) object).getConvexHull() : null;
    }

    /** Ticks until the blue stream on the wall the player stands at moves, drawn on that wall. */
    private void streamTicks(Graphics2D graphics)
    {
        LocalPoint local = wallPoints[ticksWall];
        if (local == null)
        {
            return;
        }

        Font previous = graphics.getFont();
        graphics.setFont(TICKS_FONT);
        Point location = Perspective.getCanvasTextLocation(client, graphics, local, ticksText, TICKS_HEIGHT);
        if (location != null)
        {
            OverlayUtil.renderTextLocation(graphics, location, ticksText, ticksColour);
        }
        graphics.setFont(previous);
    }

    /** Checked once per rebuild: a blue or green stream always has a model, a dry wall's object is asked. */
    private boolean hasModel(int wall)
    {
        Tile tile = advisor.wallTile(wall);
        DecorativeObject tears = tile == null ? null : tile.getDecorativeObject();
        return tears != null
            && (StreamAdvisor.colourOf(tears.getId()) != TargetRule.EMPTY || tears.getConvexHull() != null);
    }

    /** Outlines the model of the object on the wall; without one, the hull of what stands there, or its tile. */
    private void markWall(Graphics2D graphics, Mark mark)
    {
        Tile tile = advisor.wallTile(mark.wall);
        DecorativeObject tears = tile == null ? null : tile.getDecorativeObject();
        if (tears != null && mark.outline)
        {
            outlineRenderer.drawOutline(tears, mark.dim ? DIM_OUTLINE_WIDTH : OUTLINE_WIDTH, mark.colour,
                OUTLINE_FEATHER);
            return;
        }

        Shape hull = hull(mark.hullOwner);
        if (hull == null)
        {
            // nothing found at rebuild, or the object has no hull now: search the tile
            hull = hull(hullOwner(tile));
        }
        draw(graphics, hull != null ? hull : tilePoly(wallPoints[mark.wall]), mark.colour, mark.dim);
    }

    private static void draw(Graphics2D graphics, Shape shape, Color colour, boolean dim)
    {
        if (shape == null)
        {
            return;
        }

        if (dim)
        {
            Stroke previous = graphics.getStroke();
            graphics.setColor(colour);
            graphics.setStroke(DIM_STROKE);
            graphics.draw(shape);
            graphics.setStroke(previous);
        }
        else
        {
            OverlayUtil.renderPolygon(graphics, shape, colour, STROKE);
        }
    }

    private Shape tilePoly(LocalPoint local)
    {
        return local == null ? null : Perspective.getCanvasTilePoly(client, local);
    }
}
