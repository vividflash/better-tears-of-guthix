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
        position = 10
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
        position = 20
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
        position = 30
    )
    default Color secondColor()
    {
        return new Color(160, 210, 255);
    }

    @ConfigItem(
        keyName = "showPossible",
        name = "Mark possible walls",
        description = "Walls the next blue stream can land on",
        position = 40
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
        position = 50
    )
    default Color possibleMarkColor()
    {
        return new Color(255, 255, 0, 120);
    }

    @ConfigItem(
        keyName = "showBad",
        name = "Mark bad walls",
        description = "Walls no blue stream can land on next, or a green one can",
        position = 60
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
        position = 70
    )
    default Color badMarkColor()
    {
        return new Color(170, 0, 0, 120);
    }

    @ConfigItem(
        keyName = "showStreamTicks",
        name = "Stream countdown",
        description = "Ticks until your wall's blue stream moves",
        position = 80
    )
    default boolean showStreamTicks()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showDryTicks",
        name = "Dry wall countdown",
        description = "In red, ticks until the wait at a dry wall ends",
        position = 85
    )
    default boolean showDryTicks()
    {
        return false;
    }

    @ConfigItem(
        keyName = "reactionSpeed",
        name = "Reaction speed",
        description = "Ticks you take to click. At 1, the last blue stream shows the next wall a tick early, to click on the countdown",
        position = 87
    )
    default ReactionSpeed reactionSpeed()
    {
        return ReactionSpeed.INSTANT;
    }

    @ConfigItem(
        keyName = "showTearChange",
        name = "Tear change",
        description = "",
        position = 120
    )
    default boolean showTearChange()
    {
        return true;
    }

    @ConfigItem(
        keyName = "showInfo",
        name = "Info panel",
        description = "Stream order, session ticks left, and at Juna when to enter",
        position = 90
    )
    default boolean showInfo()
    {
        return true;
    }

    @ConfigItem(
        keyName = "splitTimer",
        name = "Separate timer box",
        description = "Enter timing and ticks left in their own box",
        position = 100
    )
    default boolean splitTimer()
    {
        return false;
    }

    @ConfigItem(
        keyName = "showSkill",
        name = "XP skill",
        description = "At Juna, the skill that gets the XP",
        position = 110
    )
    default boolean showSkill()
    {
        return true;
    }

    @ConfigItem(
        keyName = "refreshSkill",
        name = "Refresh XP skill",
        description = "Picks the skill again after each XP gain while Juna stays loaded",
        position = 115
    )
    default boolean refreshSkill()
    {
        return false;
    }

    @ConfigItem(
        keyName = "loginReminder",
        name = "Login reminder",
        description = "Chat message on login once the next run is open",
        position = 130
    )
    default boolean loginReminder()
    {
        return false;
    }

    @ConfigItem(
        keyName = "allowBlueFirst",
        name = "Allow BBBGGG worlds",
        description = "",
        position = 160
    )
    default boolean allowBlueFirst()
    {
        return true;
    }

    @ConfigItem(
        keyName = "deprioritiseJuna",
        name = "Deprioritise Juna",
        description = "Until the stream order is synced and supported, left click on Juna walks",
        position = 150
    )
    default boolean deprioritiseJuna()
    {
        return true;
    }

    @ConfigItem(
        keyName = "deprioritiseGreen",
        name = "Deprioritise green walls",
        description = "Left click on a green wall walks",
        position = 140
    )
    default boolean deprioritiseGreen()
    {
        return true;
    }
}
