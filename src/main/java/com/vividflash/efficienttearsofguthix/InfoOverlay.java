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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;

/** Stream order and session ticks left. The lines are rebuilt once per game tick and kept in between. */
class InfoOverlay extends OverlayPanel
{
    private final EfficientTearsOfGuthixConfig config;
    private final StreamAdvisor advisor;

    /** The advisor revision the panel's lines were built from. */
    private int builtRevision = -1;

    @Inject
    InfoOverlay(EfficientTearsOfGuthixConfig config, StreamAdvisor advisor)
    {
        this.config = config;
        this.advisor = advisor;
        setPosition(OverlayPosition.TOP_LEFT);
        setClearChildren(false);
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

            if (panelComponent.getChildren().isEmpty())
            {
                return null;
            }
            return super.render(graphics);
        }
        catch (Exception e)
        {
            advisor.report("infoOverlay", e);
            return null;
        }
    }

    private void rebuild()
    {
        List<LayoutableRenderableEntity> lines = panelComponent.getChildren();
        lines.clear();
        if (!config.showInfo())
        {
            return;
        }

        String order = advisor.orderText();
        lines.add(LineComponent.builder()
            .left("Order:")
            .right(order == null ? "syncing" : order)
            .build());

        if (order != null && !advisor.orderSupported())
        {
            lines.add(LineComponent.builder()
                .left("Order not supported")
                .leftColor(Color.RED)
                .build());
        }

        int entryWait = advisor.entryWait();
        if (entryWait == 0)
        {
            lines.add(LineComponent.builder()
                .left("Enter:")
                .right("now")
                .rightColor(Color.GREEN)
                .build());
        }
        else if (entryWait > 0)
        {
            lines.add(LineComponent.builder()
                .left("Enter in:")
                .right(entryWait + " ticks")
                .build());
        }

        int ticksLeft = advisor.ticksLeft();
        if (ticksLeft > 0)
        {
            lines.add(LineComponent.builder()
                .left("Ticks left: " + ticksLeft)
                .build());
        }
    }
}
