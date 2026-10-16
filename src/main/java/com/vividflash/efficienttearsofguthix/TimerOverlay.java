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

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/** The entry timing or session ticks left in a box of its own, updated in place once per game tick. */
class TimerOverlay extends OverlayPanel
{
    private final StreamAdvisor advisor;

    private final LineComponent timer = LineComponent.builder().build();
    private int timerRevision = -1;
    private boolean timerShown;

    @Inject
    TimerOverlay(StreamAdvisor advisor)
    {
        this.advisor = advisor;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setClearChildren(false);
        panelComponent.getChildren().add(timer);
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

            if (!advisor.splitTimer())
            {
                // Forget the build so turning the split back on rebuilds at once.
                timerRevision = -1;
                timerShown = false;
                return null;
            }

            int revision = advisor.revision();
            if (revision != timerRevision)
            {
                timerRevision = revision;
                timerShown = InfoOverlay.updateTimer(advisor, timer);
            }
            return timerShown ? super.render(graphics) : null;
        }
        catch (Exception e)
        {
            advisor.report("timerOverlay", e);
            return null;
        }
    }
}
