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

/**
 * Fixed rule for the wall to stand at. A pure function of the wall colours,
 * the blue streams' remaining ticks and the player tile.
 */
final class TargetRule
{
    static final int EMPTY = 0;
    static final int BLUE = 1;
    static final int GREEN = 2;

    static final int STOP_DELAY = 0;
    static final int START_DELAY = 2;

    /**
     * Ticks of collecting a blue stream has to offer to be worth walking to,
     * by its place among the blue streams in moving order. The wall of an
     * earlier one can still get a later one, the last one's wall cannot, so
     * the first is worth walking to for its wall alone. Chosen by simulation
     * over 0 to 4 per place, on the worst entry timing and at 0 to 2 ticks of
     * reaction.
     */
    private static final int[] MIN_COLLECT = {0, 1, 3};

    /**
     * With this many session ticks or fewer left, and the session ending as
     * the next burst starts, no later stream is worth planning for: one tick
     * of collecting is enough for any blue stream, and the one offering the
     * most ticks before the session ends is picked. Ending anywhere else in
     * the rotation, this scored worse than the plain rule in simulation.
     */
    static final int ENDGAME = 16;

    /** Session end ticks after the last blue stream's move, mod the period, at which the endgame applies. */
    private static final int[] ENDGAME_OFFSETS = {15, 0};
    private static final int PERIOD = StreamTracker.DEFAULT_PERIOD;

    /** A blue stream this close to moving counts as part of the current burst. */
    static final int BURST_WINDOW = 2;

    static final int MARK_NONE = 0;
    /** No blue stream can land here in the current rotation. */
    static final int MARK_BAD = 1;
    /** A blue stream that has not moved yet can land here. */
    static final int MARK_POSSIBLE = 2;

    /** Ticks from the first stream's move to the last one's. */
    private static final int BURST_LENGTH = 6;

    /** Ticks from the last dialog click at Juna to having control inside. */
    static final int ENTRY_LEAD = 12;
    /** Control this many ticks before the last blue stream lands is still a good start. */
    private static final int ENTRY_EARLY = 2;
    /** And this many ticks after it. */
    private static final int ENTRY_LATE = 3;

    private static final int WALLS = TogLayout.WALLS;

    static final class Decision
    {
        /** Wall to stand at, or -1. */
        final int target;
        /** Nearest reachable blue wall other than the target, or -1. */
        final int second;
        /** Wall whose collect tile the player stands on, or -1. */
        final int playerWall;

        Decision(int target, int second, int playerWall)
        {
            this.target = target;
            this.second = second;
            this.playerWall = playerWall;
        }
    }

    private TargetRule()
    {
    }

    /**
     * @param colour per wall EMPTY, BLUE or GREEN
     * @param ticksUntilMove per wall, ticks until its stream moves, at least 1; read for blue walls only
     * @param previousTarget the wall chosen last tick, or -1
     * @param walking the player moved this tick; the previous target is then kept
     *     while it can still be reached, also on another wall's collect tile
     * @param sessionLeft session ticks left, 1 on the last tick that collects, 0 when no session is running
     */
    static Decision decide(int[] colour, int[] ticksUntilMove, int playerX, int playerY, int previousTarget,
        boolean walking, int sessionLeft)
    {
        boolean endgame = sessionLeft > 0 && sessionLeft <= ENDGAME
            && endsAsBurstStarts(colour, ticksUntilMove, sessionLeft);
        int[] start = TogLayout.startTile(playerX, playerY);
        int playerWall = TogLayout.wallAtCollectTile(playerX, playerY);
        int[] distance = new int[WALLS];
        boolean[] reachable = new boolean[WALLS];
        // ticks of collecting each wall still offers; only compared in the endgame
        int[] collect = new int[WALLS];
        for (int wall = 0; wall < WALLS; wall++)
        {
            distance[wall] = TogLayout.costToWall(start[0], start[1], wall);
            int present = endgame ? Math.min(ticksUntilMove[wall], sessionLeft) : ticksUntilMove[wall];
            collect[wall] = present - STOP_DELAY - distance[wall] - START_DELAY;
            int needed = endgame
                ? 1
                : MIN_COLLECT[Math.min(MIN_COLLECT.length - 1, blueRank(wall, colour, ticksUntilMove))];
            reachable[wall] = colour[wall] == BLUE && collect[wall] >= needed;
        }
        int[] prefer = endgame ? collect : null;

        int target;
        if ((playerWall < 0 || walking) && previousTarget >= 0 && previousTarget < WALLS && reachable[previousTarget])
        {
            target = previousTarget;
        }
        else if (playerWall < 0)
        {
            target = nearestBlue(-1, reachable, distance, ticksUntilMove, prefer);
        }
        else if (colour[playerWall] == BLUE)
        {
            if (lastOfBurst(playerWall, colour, ticksUntilMove))
            {
                int other = nearestBlue(playerWall, reachable, distance, ticksUntilMove, prefer);
                target = other >= 0 ? other : playerWall;
            }
            else
            {
                target = playerWall;
            }
        }
        else if (colour[playerWall] == GREEN)
        {
            int other = nearestBlue(playerWall, reachable, distance, ticksUntilMove, prefer);
            target = other >= 0 ? other : nearestNotGreen(playerWall, colour, distance);
        }
        else if (pendingBlueMove(playerWall, colour, ticksUntilMove))
        {
            target = playerWall;
        }
        else
        {
            int other = nearestBlue(playerWall, reachable, distance, ticksUntilMove, prefer);
            target = other >= 0 ? other : playerWall;
        }

        int second = -1;
        if (target >= 0)
        {
            reachable[target] = false;
            second = nearestBlue(-1, reachable, distance, ticksUntilMove, prefer);
        }

        return new Decision(target, second, playerWall);
    }

    /** How many other blue streams move before the one on this wall. */
    private static int blueRank(int wall, int[] colour, int[] ticksUntilMove)
    {
        int rank = 0;
        for (int other = 0; other < WALLS; other++)
        {
            if (other != wall && colour[other] == BLUE && ticksUntilMove[other] < ticksUntilMove[wall])
            {
                rank++;
            }
        }
        return rank;
    }

    /**
     * Where the blue streams that are about to move can and cannot land. Once
     * a green stream has moved, its new wall is bad and so is the wall of the
     * blue stream that moves last. Once all three have moved, the empty walls
     * and the walls of the other waiting blue streams are possible. No marks
     * outside that window.
     *
     * @param ticksUntilMove per wall, ticks until its stream moves, at least 1
     */
    static int[] marks(int[] colour, int[] ticksUntilMove, int period, boolean blueFirst)
    {
        return blueFirst ? blueFirstMarks(colour, ticksUntilMove, period) : marks(colour, ticksUntilMove, period);
    }

    /**
     * The same for an order in which the blue streams move first. From one tick
     * before the first blue stream moves, the empty walls and the walls of the
     * blue streams that move before the last one are possible, and the last
     * one's wall is bad. Once all three have landed and until the green streams
     * have, every wall a green stream can land on is bad.
     */
    private static int[] blueFirstMarks(int[] colour, int[] ticksUntilMove, int period)
    {
        int[] marks = new int[WALLS];
        int half = BURST_LENGTH / 2;
        boolean blueMoved = false;
        int lastBlue = -1;
        int lastGreen = -1;
        int soonest = Integer.MAX_VALUE;
        for (int wall = 0; wall < WALLS; wall++)
        {
            int ticks = ticksUntilMove[wall];
            if (colour[wall] == BLUE)
            {
                blueMoved |= ticks > period - half;
                soonest = Math.min(soonest, ticks);
                if (ticks <= half && (lastBlue < 0 || ticks > ticksUntilMove[lastBlue]))
                {
                    lastBlue = wall;
                }
            }
            else if (colour[wall] == GREEN && ticks <= half
                && (lastGreen < 0 || ticks > ticksUntilMove[lastGreen]))
            {
                lastGreen = wall;
            }
        }

        if (lastBlue >= 0)
        {
            if (!blueMoved && soonest > 1)
            {
                return marks;
            }

            for (int wall = 0; wall < WALLS; wall++)
            {
                if (colour[wall] == EMPTY || (colour[wall] == BLUE && ticksUntilMove[wall] <= half))
                {
                    marks[wall] = MARK_POSSIBLE;
                }
            }
            marks[lastBlue] = MARK_BAD;
        }
        else if (blueMoved && lastGreen >= 0)
        {
            for (int wall = 0; wall < WALLS; wall++)
            {
                if (colour[wall] == EMPTY
                    || (colour[wall] == GREEN && wall != lastGreen && ticksUntilMove[wall] <= half))
                {
                    marks[wall] = MARK_BAD;
                }
            }
        }
        return marks;
    }

    /** Marks for the order in which the green streams move first. */
    static int[] marks(int[] colour, int[] ticksUntilMove, int period)
    {
        int[] marks = new int[WALLS];
        int movedGreens = 0;
        int last = -1;
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (colour[wall] == GREEN)
            {
                if (ticksUntilMove[wall] > period - BURST_LENGTH)
                {
                    marks[wall] = MARK_BAD;
                    movedGreens++;
                }
            }
            else if (colour[wall] == BLUE && ticksUntilMove[wall] < BURST_LENGTH
                && (last < 0 || ticksUntilMove[wall] > ticksUntilMove[last]))
            {
                last = wall;
            }
        }

        if (movedGreens == 0 || last < 0)
        {
            return new int[WALLS];
        }

        marks[last] = MARK_BAD;
        if (movedGreens < 3)
        {
            return marks;
        }

        for (int wall = 0; wall < WALLS; wall++)
        {
            if (colour[wall] == EMPTY
                || (colour[wall] == BLUE && wall != last && ticksUntilMove[wall] < BURST_LENGTH))
            {
                marks[wall] = MARK_POSSIBLE;
            }
        }
        return marks;
    }

    /**
     * Ticks to wait before the last dialog click at Juna, so that control
     * inside starts around the tick the last blue stream lands. 0 means now.
     *
     * @param lastBlueMove a tick on which the last blue stream of the rotation moves
     */
    static int entryWait(int lastBlueMove, int period, int now)
    {
        int untilBest = Math.floorMod(lastBlueMove - ENTRY_LEAD - now, period);
        if (untilBest <= ENTRY_EARLY || untilBest >= period - ENTRY_LATE)
        {
            return 0;
        }
        return untilBest - ENTRY_EARLY;
    }

    /**
     * The move tick of the blue stream that moves last, from the spawn ticks of
     * the three blue streams. They move on consecutive ticks, also when some of
     * them have already moved in the current rotation.
     */
    static int lastBlueMove(int[] blueSpawnTicks, int period)
    {
        for (int candidate : blueSpawnTicks)
        {
            boolean before = false;
            boolean twoBefore = false;
            for (int other : blueSpawnTicks)
            {
                int gap = Math.floorMod(candidate - other, period);
                before |= gap == 1;
                twoBefore |= gap == 2;
            }
            if (before && twoBefore)
            {
                return candidate + period;
            }
        }

        int latest = blueSpawnTicks[0];
        for (int tick : blueSpawnTicks)
        {
            latest = Math.max(latest, tick);
        }
        return latest + period;
    }

    /** The stream here is about to move and every other blue one has already moved on. */
    private static boolean lastOfBurst(int playerWall, int[] colour, int[] ticksUntilMove)
    {
        int own = ticksUntilMove[playerWall];
        if (own > BURST_WINDOW)
        {
            return false;
        }

        for (int wall = 0; wall < WALLS; wall++)
        {
            if (wall != playerWall && colour[wall] == BLUE && ticksUntilMove[wall] <= own + BURST_WINDOW)
            {
                return false;
            }
        }
        return true;
    }

    private static boolean pendingBlueMove(int playerWall, int[] colour, int[] ticksUntilMove)
    {
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (wall != playerWall && colour[wall] == BLUE && ticksUntilMove[wall] <= BURST_WINDOW)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the session ends 15 or 16 ticks after the last blue stream of a
     * burst moves, as the next burst starts. The last blue is the one moving
     * 1 and 2 ticks after the other two; its next move is ticksUntilMove away.
     */
    static boolean endsAsBurstStarts(int[] colour, int[] ticksUntilMove, int sessionLeft)
    {
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (colour[wall] != BLUE)
            {
                continue;
            }
            boolean one = false;
            boolean two = false;
            for (int other = 0; other < WALLS; other++)
            {
                if (other != wall && colour[other] == BLUE)
                {
                    int gap = Math.floorMod(ticksUntilMove[wall] - ticksUntilMove[other], PERIOD);
                    one |= gap == 1;
                    two |= gap == 2;
                }
            }
            if (one && two)
            {
                int offset = Math.floorMod(sessionLeft - ticksUntilMove[wall], PERIOD);
                for (int endgameOffset : ENDGAME_OFFSETS)
                {
                    if (offset == endgameOffset)
                    {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Nearest reachable blue wall; on a tie the one moving soonest, then the
     * lower index. With {@code prefer} given, the wall with the highest value
     * there wins before distance is looked at. -1 if none.
     */
    private static int nearestBlue(int excluded, boolean[] reachable, int[] distance, int[] ticksUntilMove,
        int[] prefer)
    {
        int best = -1;
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (wall == excluded || !reachable[wall])
            {
                continue;
            }
            if (best < 0)
            {
                best = wall;
                continue;
            }
            if (prefer != null && prefer[wall] != prefer[best])
            {
                if (prefer[wall] > prefer[best])
                {
                    best = wall;
                }
                continue;
            }
            if (distance[wall] < distance[best]
                || (distance[wall] == distance[best] && ticksUntilMove[wall] < ticksUntilMove[best]))
            {
                best = wall;
            }
        }
        return best;
    }

    private static int nearestNotGreen(int excluded, int[] colour, int[] distance)
    {
        int best = -1;
        for (int wall = 0; wall < WALLS; wall++)
        {
            if (wall != excluded && colour[wall] != GREEN && (best < 0 || distance[wall] < distance[best]))
            {
                best = wall;
            }
        }
        return best;
    }
}
