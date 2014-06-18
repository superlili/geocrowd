/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.geocrowd.setcover;

import java.util.ArrayList;
import java.util.HashSet;

/**
 *
 * @author Luan
 */
public class SetCoverGreedySmallestAssociateSet {

    ArrayList<HashSet<Integer>> setOfSets = null;
    HashSet<Integer> universe = null;

    /**
     * Initialize variables
     *
     * @param container
     */
    public SetCoverGreedySmallestAssociateSet(ArrayList<ArrayList> container) {
        setOfSets = new ArrayList<>();
        universe = new HashSet<>();

        for (int i = 0; i < container.size(); i++) {
            ArrayList<Integer> items = container.get(i);
            if (items != null) {
                HashSet<Integer> itemSet = new HashSet<Integer>(items);
                setOfSets.add(itemSet);
                universe.addAll(itemSet);
            }
        }
    }

    /**
     * Compute associates sets for uncovered elements in a set
     *
     * @param S
     * @param s
     * @param C
     * @return
     */
    public int computeAssociateSets(ArrayList<HashSet<Integer>> S, HashSet<Integer> s, HashSet<Integer> C) {
        int numAssociateSet = 0;
        for (Integer i : s) {
            if (!C.contains(i)) {
                for (HashSet<Integer> s2 : S) {
                    if (s2.contains(i)) {
                        numAssociateSet++;
                    }
                }
            }
        }
        return numAssociateSet;
    }

    /**
     * Greedy algorithm
     */
    public int minSetCover() {
        ArrayList<HashSet<Integer>> S = (ArrayList<HashSet<Integer>>) setOfSets.clone();
        HashSet<Integer> Q = (HashSet<Integer>) universe.clone();
        HashSet<Integer> C = new HashSet<Integer>();

        int set_size = S.size();

        while (!Q.isEmpty()) {
            HashSet<Integer> maxSet = null;
            int maxElem = 0;
            int numAssociateSet = 0;
            for (HashSet<Integer> s : S) {
                // select the item set that maximize coverage
                // how many elements in s that are not in C
                int newElem = 0;
                
                for (Integer i : s) {
                    if (!C.contains(i)) {
                        newElem++;
                    }
                }
                if (newElem > maxElem) {
                    maxElem = newElem;
                    maxSet = s;
                    numAssociateSet = computeAssociateSets(S, s, C);
                } else if (newElem == maxElem) //compare associate sets 
                {
                    int n = computeAssociateSets(S, s, C);
                    if (n < numAssociateSet) {
                        maxElem = newElem;
                        maxSet = s;
                        numAssociateSet = n;
                    }
                }
            }

            S.remove(maxSet);
            Q.removeAll(maxSet);
            C.addAll(maxSet);
        }

        return set_size - S.size();
    }
}