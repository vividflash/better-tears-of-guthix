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
import java.util.Arrays;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.ColorUtil;

/**
 * Follows the streams, keeps the session countdown and applies the rule for
 * the wall to stand at. Every entry point catches its own failures, so a
 * fault is logged once instead of on every tick or frame. What the overlays
 * and the menu handler need is worked out once per game tick and cached.
 */
@Slf4j
@Singleton
public class StreamAdvisor
{
    private static final int MAX_REPORTS = 200;
    private static final int[] NO_MARKS = new int[TogLayout.WALLS];
    private static final int MAX_TEAR_CHANGES = 10;
    /** How long a tear counter change stays on screen. */
    static final long TEAR_CHANGE_MILLIS = 1800;

    private static final Color BLUE_STREAM = new Color(0, 150, 255);
    private static final Color GREEN_STREAM = Color.GREEN;

    /** One change of the tear counter, with its text ready to draw. */
    static final class TearChange
    {
        final String text;
        final boolean gain;
        final long millis;

        TearChange(int change, long millis)
        {
            this.text = change > 0 ? "+" + change : Integer.toString(change);
            this.gain = change > 0;
            this.millis = millis;
        }
    }

    private final StreamTracker tracker = new StreamTracker();
    private final Set<String> reported = ConcurrentHashMap.newKeySet();
    private final int[] seen = new int[TogLayout.WALLS];
    private final int[] colours = new int[TogLayout.WALLS];
    private final int[] untilMove = new int[TogLayout.WALLS];
    /** The wall tiles as of the last game tick, null where not in the scene. */
    private final Tile[] wallTiles = new Tile[TogLayout.WALLS];

    @Inject
    private Client client;

    @Inject
    private EfficientTearsOfGuthixConfig config;

    private volatile int target = -1;
    private volatile int second = -1;
    /** Per wall TargetRule mark. Replaced, never written to. */
    private volatile int[] marks = NO_MARKS;
    private volatile int ownWall = -1;
    private volatile int ownTicks;
    private volatile boolean shouldMove;
    private volatile int entryWait = -1;

    /** Player in the region, and on the cave's plane, as of the last game tick. */
    private volatile boolean inRegion;
    private volatile boolean onPlane;
    private volatile boolean synced;
    /** Stream order as of the last game tick, null while not synced. */
    private volatile String streamOrder;
    /** The same with each letter in its stream's colour. */
    private volatile String orderText;
    private volatile boolean orderSupported;
    /** Changes whenever the cached state does; the overlays rebuild what they draw when it has. */
    private volatile int revision;
    /** Changes only with the order, the XP skill, being at Juna or a setting. */
    private volatile int infoRevision;
    private volatile boolean showInfo;
    private volatile boolean splitTimer;
    /** False from a clear until the first game tick in the region: lastGameTick is stale until then. */
    private volatile boolean ticked;
    private boolean deprioritiseJuna;
    private boolean deprioritiseGreen;

    /** Recent tear counter changes, oldest first. */
    private final Deque<TearChange> tearChanges = new ConcurrentLinkedDeque<>();
    private int lastTears = -1;
    /** False from the countdown being set until it first drops: the walk in. */
    private boolean counting;
    /** Player tile on the previous tick, -1 when unknown. */
    private int lastX = -1;
    private int lastY = -1;

    /** Tick of the last game tick seen. Events before the next one are stamped with it plus one. */
    private int lastGameTick;
    private int ticksLeft;
    private int lastCountdown = -1;
    /** Something is tracked that leaving the region has to clear; outside it nothing else runs. */
    private boolean needsClear;
    /** In the region, no session running and off the cave floor: at Juna. */
    private boolean atEntrance;

    void start()
    {
        try
        {
            configChanged();
            clear();
        }
        catch (Exception e)
        {
            report("start", e);
        }
    }

    void stop()
    {
        try
        {
            clear();
        }
        catch (Exception e)
        {
            report("stop", e);
        }
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
    {
        try
        {
            streamEvent(event.getDecorativeObject(), true);
        }
        catch (Exception e)
        {
            report("onDecorativeObjectSpawned", e);
        }
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
    {
        try
        {
            streamEvent(event.getDecorativeObject(), false);
        }
        catch (Exception e)
        {
            report("onDecorativeObjectDespawned", e);
        }
    }

    /** A world hop or scene load can put other streams on the walls: nothing tracked carries over. */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        try
        {
            if (event.getGameState() != GameState.LOGGED_IN)
            {
                clear();
            }
        }
        catch (Exception e)
        {
            report("onGameStateChanged", e);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        try
        {
            Player local = client.getLocalPlayer();
            WorldPoint position = local == null ? null : local.getWorldLocation();
            if (position == null || position.getRegionID() != TogLayout.REGION)
            {
                if (needsClear)
                {
                    clear();
                }
                return;
            }

            int tick = client.getTickCount();
            lastGameTick = tick;
            ticked = true;

            int countdown = client.getVarbitValue(VarbitID.TOG_COUNTDOWN);
            if (countdown != lastCountdown)
            {
                counting = lastCountdown > 0 && countdown < lastCountdown;
                lastCountdown = countdown;
                ticksLeft = Math.max(0, countdown);
            }
            else if (counting && ticksLeft > 0)
            {
                ticksLeft--;
            }

            inRegion = true;
            needsClear = true;
            WorldView view = client.getTopLevelWorldView();
            onPlane = view != null && view.getPlane() == TogLayout.PLANE;
            boolean entrance = lastCountdown <= 0 && !TogLayout.isFloor(position.getX(), position.getY());
            if (entrance != atEntrance)
            {
                atEntrance = entrance;
                infoRevision++;
            }
            deprioritiseJuna = config.deprioritiseJuna();
            deprioritiseGreen = config.deprioritiseGreen();

            tearChange(client.getVarbitValue(VarbitID.TOG_TEARS_COLLECTED));

            if (readWalls(view))
            {
                tracker.resync(seen, tick);
            }

            synced = tracker.isSynced();
            String order = tracker.order();
            if (!Objects.equals(order, streamOrder))
            {
                streamOrder = order;
                orderText = order == null ? null : coloured(order);
                infoRevision++;
            }
            boolean supported = supported(order);
            if (supported != orderSupported)
            {
                orderSupported = supported;
                infoRevision++;
            }

            decide(tick, position);
            revision++;
        }
        catch (Exception e)
        {
            report("onGameTick", e);
            clear();
        }
    }

    /**
     * A left click on a wall with a green stream walks there instead of
     * collecting, and on a world with another stream order a left click on
     * Juna walks instead of talking.
     */
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        try
        {
            MenuEntry entry = event.getMenuEntry();
            if (entry == null || !isObjectOption(entry.getType()) || !inArea())
            {
                return;
            }

            if (entry.getIdentifier() == ObjectID.TOG_JUNA)
            {
                if (deprioritiseJuna && streamOrder != null && !orderSupported)
                {
                    entry.setDeprioritized(true);
                }
                return;
            }

            if (!deprioritiseGreen || !onPlane)
            {
                return;
            }

            WorldView view = client.getTopLevelWorldView();
            if (view == null)
            {
                return;
            }

            // the tracker follows the walls on every tick, synced or not
            int wall = TogLayout.wallOnTile(view.getBaseX() + entry.getParam0(), view.getBaseY() + entry.getParam1());
            if (wall >= 0 && tracker.colour(wall) == TargetRule.GREEN)
            {
                entry.setDeprioritized(true);
            }
        }
        catch (Exception e)
        {
            report("onMenuEntryAdded", e);
        }
    }

    private static boolean isObjectOption(MenuAction type)
    {
        return type == MenuAction.GAME_OBJECT_FIRST_OPTION
            || type == MenuAction.GAME_OBJECT_SECOND_OPTION
            || type == MenuAction.GAME_OBJECT_THIRD_OPTION
            || type == MenuAction.GAME_OBJECT_FOURTH_OPTION
            || type == MenuAction.GAME_OBJECT_FIFTH_OPTION;
    }

    /** Logged in, and in the region as of the last game tick. */
    boolean inArea()
    {
        return inRegion;
    }

    /** The top level view was on the cave's plane on the last game tick. */
    boolean onPlane()
    {
        return onPlane;
    }

    boolean atEntrance()
    {
        return atEntrance;
    }

    boolean isSynced()
    {
        return synced;
    }

    /** The rule runs on this order. */
    private boolean supported(String order)
    {
        return StreamTracker.EXPECTED_ORDER.equals(order)
            || (StreamTracker.BLUE_FIRST_ORDER.equals(order) && config.allowBlueFirst());
    }

    /** The rule runs on the current order. False while not synced. */
    boolean orderSupported()
    {
        return orderSupported;
    }

    /** The stream order with each letter in its stream's colour. Null while not synced. */
    String orderText()
    {
        return orderText;
    }

    int revision()
    {
        return revision;
    }

    int infoRevision()
    {
        return infoRevision;
    }

    /** Something the info panel shows changed outside the advisor. */
    void infoChanged()
    {
        infoRevision++;
    }

    /** Reads the panel settings again; called on start and on a change of this plugin's config. */
    void configChanged()
    {
        showInfo = config.showInfo();
        splitTimer = config.splitTimer();
        infoRevision++;
    }

    boolean showInfo()
    {
        return showInfo;
    }

    boolean splitTimer()
    {
        return splitTimer;
    }

    /** The tile of this wall as of the last game tick, or null. */
    Tile wallTile(int wall)
    {
        return wallTiles[wall];
    }

    int ticksLeft()
    {
        return ticksLeft;
    }

    /** Wall to stand at, or -1 when there is no current answer. */
    int targetWall()
    {
        return target;
    }

    /** Next best wall, or -1. */
    int secondWall()
    {
        return second;
    }

    /** Wall whose collect tile the player stands on, or -1. */
    int ownWall()
    {
        return ownWall;
    }

    /** Ticks until the blue stream on the wall the player stands at moves, 0 when there is none. */
    int ownTicks()
    {
        return ownTicks;
    }

    /** The wall the rule picked is not the one the player stands at. */
    boolean shouldMove()
    {
        return shouldMove;
    }

    /** Ticks to wait before the last dialog click at Juna, 0 for now, -1 when not waiting to enter. */
    int entryWait()
    {
        return entryWait;
    }

    Deque<TearChange> tearChanges()
    {
        return tearChanges;
    }

    /** Per wall TargetRule mark. Read only. */
    int[] wallMarks()
    {
        return marks;
    }

    /** Logs each distinct failure once. */
    void report(String where, Exception e)
    {
        String key = where + ": " + e;
        if (reported.size() < MAX_REPORTS && reported.add(key))
        {
            log.warn("stream advisor failure in {}", where, e);
        }
    }

    private void clear()
    {
        needsClear = false;
        ticked = false;
        target = -1;
        second = -1;
        marks = NO_MARKS;
        ownWall = -1;
        ownTicks = 0;
        shouldMove = false;
        entryWait = -1;
        lastTears = -1;
        lastX = -1;
        lastY = -1;
        lastCountdown = -1;
        ticksLeft = 0;
        counting = false;
        tearChanges.clear();
        tracker.reset();
        if (atEntrance || streamOrder != null || orderSupported)
        {
            infoRevision++;
        }
        inRegion = false;
        onPlane = false;
        atEntrance = false;
        synced = false;
        streamOrder = null;
        orderText = null;
        orderSupported = false;
        Arrays.fill(wallTiles, null);
        revision++;
    }

    /**
     * The change of the tear counter to show, 0 for none. A fall to 0 from more
     * than 1 is the counter being reset for a new session, not a loss. A fall
     * from exactly 1 cannot be told apart from a loss and is shown.
     */
    static int shownChange(int last, int now)
    {
        if (last < 0 || now == last || (now == 0 && last != 1))
        {
            return 0;
        }
        return now - last;
    }

    /** Records a change of the tear counter and drops the ones that have run their time. */
    private void tearChange(int tears)
    {
        long now = System.currentTimeMillis();
        TearChange oldest;
        while ((oldest = tearChanges.peekFirst()) != null && now - oldest.millis >= TEAR_CHANGE_MILLIS)
        {
            tearChanges.pollFirst();
        }

        int change = shownChange(lastTears, tears);
        if (change != 0)
        {
            tearChanges.addLast(new TearChange(change, now));
            while (tearChanges.size() > MAX_TEAR_CHANGES)
            {
                tearChanges.pollFirst();
            }
        }
        lastTears = tears;
    }

    /** Each letter in its stream's colour. */
    private static String coloured(String order)
    {
        StringBuilder text = new StringBuilder(order.length() * 20);
        for (int i = 0; i < order.length(); i++)
        {
            char stream = order.charAt(i);
            text.append(ColorUtil.prependColorTag(String.valueOf(stream), stream == 'G' ? GREEN_STREAM : BLUE_STREAM));
        }
        return text.toString();
    }

    /**
     * Only live observations count; the per tick wall read covers everything
     * else. That includes anything before the first game tick after a clear,
     * which could not be given a tick.
     */
    private void streamEvent(DecorativeObject object, boolean spawned)
    {
        if (object == null)
        {
            return;
        }

        int colour = colourOf(object.getId());
        if (colour == TargetRule.EMPTY || !ticked || client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        WorldPoint point = object.getWorldLocation();
        if (point == null || point.getPlane() != TogLayout.PLANE)
        {
            return;
        }

        int wall = TogLayout.wallOnTile(point.getX(), point.getY());
        if (wall < 0)
        {
            return;
        }

        int tick = lastGameTick + 1;
        needsClear = true;
        if (spawned)
        {
            tracker.spawn(wall, colour, tick);
        }
        else
        {
            tracker.despawn(wall, colour, tick);
        }
    }

    static int colourOf(int objectId)
    {
        switch (objectId)
        {
            case ObjectID.TOG_WEEPING_WALL_GOOD_R:
            case ObjectID.TOG_WEEPING_WALL_GOOD_L:
                return TargetRule.BLUE;
            case ObjectID.TOG_WEEPING_WALL_BAD_R:
            case ObjectID.TOG_WEEPING_WALL_BAD_L:
                return TargetRule.GREEN;
            default:
                return TargetRule.EMPTY;
        }
    }

    /**
     * Reads the nine wall tiles into {@code seen} and keeps the tiles for the
     * overlay. False when any of them is not in the scene.
     */
    private boolean readWalls(WorldView view)
    {
        Arrays.fill(wallTiles, null);
        Scene scene = view == null ? null : view.getScene();
        Tile[][][] tiles = scene == null ? null : scene.getTiles();
        if (tiles == null || tiles.length <= TogLayout.PLANE || tiles[TogLayout.PLANE] == null)
        {
            return false;
        }

        Tile[][] plane = tiles[TogLayout.PLANE];
        boolean complete = true;
        for (int wall = 0; wall < TogLayout.WALLS; wall++)
        {
            LocalPoint local = LocalPoint.fromWorld(view, TogLayout.wallX(wall), TogLayout.wallY(wall));
            int x = local == null ? -1 : local.getSceneX();
            int y = local == null ? -1 : local.getSceneY();
            if (x < 0 || x >= plane.length || plane[x] == null || y < 0 || y >= plane[x].length)
            {
                complete = false;
                continue;
            }

            Tile tile = plane[x][y];
            wallTiles[wall] = tile;
            DecorativeObject object = tile == null ? null : tile.getDecorativeObject();
            seen[wall] = object == null ? TargetRule.EMPTY : colourOf(object.getId());
        }
        return complete;
    }

    /** Applies the rule while the streams are synced in the expected order; otherwise there is no target. */
    private void decide(int tick, WorldPoint position)
    {
        TargetRule.Decision decision = null;
        String order = streamOrder;
        if (orderSupported)
        {
            int period = tracker.period();
            for (int wall = 0; wall < TogLayout.WALLS; wall++)
            {
                colours[wall] = tracker.colour(wall);
                untilMove[wall] = Math.max(1, tracker.spawnTick(wall) + period - tick);
            }
            boolean walking = lastX >= 0 && (lastX != position.getX() || lastY != position.getY());
            decision = TargetRule.decide(colours, untilMove, position.getX(), position.getY(), target, walking,
                ticksLeft);
            marks = TargetRule.marks(colours, untilMove, period, StreamTracker.BLUE_FIRST_ORDER.equals(order));
        }
        else
        {
            marks = NO_MARKS;
        }

        lastX = position.getX();
        lastY = position.getY();

        int standing = decision == null ? -1 : decision.playerWall;
        ownWall = standing;
        ownTicks = standing >= 0 && colours[standing] == TargetRule.BLUE ? untilMove[standing] : 0;
        shouldMove = decision != null && decision.target >= 0 && decision.target != standing;
        entryWait = decision != null && lastCountdown <= 0 && !TogLayout.isFloor(position.getX(), position.getY())
            ? entryWait(tick)
            : -1;

        target = decision == null ? -1 : decision.target;
        second = decision == null ? -1 : decision.second;
    }

    private int entryWait(int tick)
    {
        int[] spawns = new int[StreamTracker.STREAMS / 2];
        int count = 0;
        for (int wall = 0; wall < TogLayout.WALLS && count < spawns.length; wall++)
        {
            if (colours[wall] == TargetRule.BLUE)
            {
                spawns[count++] = tracker.spawnTick(wall);
            }
        }
        if (count < spawns.length)
        {
            return -1;
        }
        return TargetRule.entryWait(TargetRule.lastBlueMove(spawns, tracker.period()), tracker.period(), tick);
    }
}
