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
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("efficienttearsofguthix")
public interface EfficientTearsOfGuthixConfig extends Config
{
    @ConfigItem(
        keyName = "highlightMode",
        name = "Highlight",
        description = "",
        position = 1
    )
    default HighlightMode highlightMode()
    {
        return HighlightMode.BOTH;
    }

    @Alpha
    @ConfigItem(
        keyName = "bestColor",
        name = "Best wall colour",
        description = "",
        position = 2
    )
    default Color bestColor()
    {
        return new Color(0, 150, 255);
    }

    @Alpha
    @ConfigItem(
        keyName = "secondColor",
        name = "Second best colour",
        description = "",
        position = 3
    )
    default Color secondColor()
    {
        return new Color(160, 210, 255);
    }

    @ConfigItem(
        keyName = "showPossible",
        name = "Mark possible walls",
        description = "Walls the blue streams that are about to move can land on",
        position = 4
    )
    default boolean showPossible()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
        keyName = "possibleMarkColor",
        name = "Possible wall colour",
        description = "",
        position = 5
    )
    default Color possibleMarkColor()
    {
        return new Color(255, 255, 0, 120);
    }

    @ConfigItem(
        keyName = "showBad",
        name = "Mark bad walls",
        description = "Ground in front of walls where no blue stream can land next, or a green one can",
        position = 6
    )
    default boolean showBad()
    {
        return false;
    }

    @Alpha
    @ConfigItem(
        keyName = "badMarkColor",
        name = "Bad wall colour",
        description = "",
        position = 7
    )
    default Color badMarkColor()
    {
        return new Color(170, 0, 0, 120);
    }

    @ConfigItem(
        keyName = "showStreamTicks",
        name = "Stream countdown",
        description = "Ticks until the blue stream on the wall you stand at moves",
        position = 8
    )
    default boolean showStreamTicks()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showTearChange",
        name = "Tear change",
        description = "Beside the tear counter",
        position = 10
    )
    default boolean showTearChange()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showInfo",
        name = "Info panel",
        description = "Stream order, when to enter and the session ticks left",
        position = 9
    )
    default boolean showInfo()
    {
        return true;
    }

    @ConfigItem(
        keyName = "allowBlueFirst",
        name = "Allow BBBGGG worlds",
        description = "Blue streams move first there, so leaving a dry wall late costs tears",
        position = 13
    )
    default boolean allowBlueFirst()
    {
        return true;
    }

    @ConfigItem(
        keyName = "deprioritiseJuna",
        name = "Deprioritise Juna",
        description = "On a world whose stream order is not supported, a left click on Juna walks instead of talking",
        position = 12
    )
    default boolean deprioritiseJuna()
    {
        return true;
    }

    @ConfigItem(
        keyName = "deprioritiseGreen",
        name = "Deprioritise green walls",
        description = "A left click on a wall with a green stream walks instead of collecting",
        position = 11
    )
    default boolean deprioritiseGreen()
    {
        return true;
    }
}
