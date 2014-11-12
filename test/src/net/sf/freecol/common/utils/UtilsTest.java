/**
 *  Copyright (C) 2002-2014  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.utils;

import java.util.ArrayList;
import java.util.List;

import net.sf.freecol.common.util.Utils;

import net.sf.freecol.util.test.FreeColTestCase;


public class UtilsTest extends FreeColTestCase {

    private List<Integer> makeList(int... args) {
        List<Integer> l = new ArrayList<Integer>();
        for (int a : args) l.add(Integer.valueOf(a));
        return l;
    }

    public void testGetPermutations() {
        List<Integer> l = new ArrayList<Integer>();
        l.add(Integer.valueOf(1));
        l.add(Integer.valueOf(2));
        l.add(Integer.valueOf(3));
        List<List<Integer>> p = new ArrayList<List<Integer>>();
        try {
            for (List<Integer> li : Utils.getPermutations(l)) p.add(li);
        } catch (Exception e) {
            fail();
        }
        assertEquals(p.size(), 6);
        assertEquals(p.get(0), makeList(1,2,3));
        assertEquals(p.get(1), makeList(1,3,2));
        assertEquals(p.get(2), makeList(2,1,3));
        assertEquals(p.get(3), makeList(2,3,1));
        assertEquals(p.get(4), makeList(3,1,2));
        assertEquals(p.get(5), makeList(3,2,1));
    }
}
