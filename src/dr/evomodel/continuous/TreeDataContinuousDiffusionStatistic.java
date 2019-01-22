/*
 * ContinuousDiffusionStatistic.java
 *
 * Copyright (c) 2002-2015 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.continuous;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.tree.TreeTrait;
import dr.evomodel.tree.TreeStatistic;
import dr.evomodel.treedatalikelihood.TreeDataLikelihood;
import dr.evomodel.treedatalikelihood.continuous.ContinuousDataLikelihoodDelegate;
import dr.xml.*;

import static dr.evomodelxml.treelikelihood.TreeTraitParserUtilities.TRAIT_NAME;

/**
 * @author Marc A. Suchard
 * @author Philippe Lemey
 * @author Andrew Holbrook
 * @author Alex Fisher
 */

public class TreeDataContinuousDiffusionStatistic extends TreeStatistic {

    private static final String CONTINUOUS_DIFFUSION_STATISTIC = "traitDataContinuousDiffusionStatistic";

    private TreeDataContinuousDiffusionStatistic(String statisticName,
                                                 TreeTrait.DA trait,
                                                 TreeDataLikelihood likelihood,
                                                 WeightingScheme weightingScheme,
                                                 DisplacementScheme displacementScheme) {
        super(statisticName);
        this.trait = trait;
        this.tree = likelihood.getTree();
        this.weightedScheme = weightingScheme;
        this.displacementScheme = displacementScheme;
    }

    @Override
    public void setTree(Tree tree) {
        throw new RuntimeException("Cannot set the tree");
    }

    @Override
    public Tree getTree() {
        return tree;
    }

    @Override
    public int getDimension() {
        return 1;
    }

    @Override
    public double getStatisticValue(int dim) {

        Statistic total = new Statistic();

        for (int i = 0; i < tree.getNodeCount(); ++i) {
            NodeRef node = tree.getNode(i);
            if (node != tree.getRoot()) {
                addBranchStatistic(total, node);
            }
        }

        return total.numerator / total.denominator;
    }

    private void addBranchStatistic(Statistic lhs, NodeRef child) {

        NodeRef parent = tree.getParent(child);

        double[] parentTrait = trait.getTrait(tree, parent);
        double[] childTrait = trait.getTrait(tree, child);

        double displacement = displacementScheme.distance(parentTrait, childTrait); // added "displacementScheme"
        double time = tree.getNodeHeight(parent) - tree.getNodeHeight(child);

        weightedScheme.add(lhs, displacement, time);
    }

    private static double distance(double[] x, double[] y) {
        assert (x.length == y.length);

        double total = 0.0;
        for (int i = 0; i < x.length; ++i) {
            total += (x[i] - y[i]) * (x[i] - y[i]);
        }

        return total; // total = square displacement
    }

    private final TreeTrait.DA trait;
    private final Tree tree;
    private final WeightingScheme weightedScheme;
    private final DisplacementScheme displacementScheme;

    private static final String WEIGHT = "weight";
    private static final String DISPLACEMENT_SCHEME = "displacementScheme";

    private enum DisplacementScheme {
        LINEAR {
            @Override
            double distance(double[] x, double[] y) {
                assert (x.length == y.length);

                double total = 0.0;
                for (int i = 0; i < x.length; ++i) {
                    total += (x[i] - y[i]) * (x[i] - y[i]);
                }

                return Math.sqrt(total); //linear
            }
            @Override
            String getName() { return "linear"; }
        },
        QUADRATIC {
            @Override
            double distance(double[] x, double[] y) {
                assert (x.length == y.length);

                double total = 0.0;
                for (int i = 0; i < x.length; ++i) {
                    total += (x[i] - y[i]) * (x[i] - y[i]);
                }
                return total; //quadratic
            }
            @Override
            String getName() { return "quadratic"; }
        };
        abstract double distance(double[] x, double[] y);
        abstract String getName();
    }

    private enum WeightingScheme {
        WEIGHTED {
            @Override
            void add(Statistic lhs, double displacement, double time) {
                 lhs.numerator += displacement;
                 lhs.denominator += time;
            }

            @Override
            String getName() { return "weighted"; }
        },
        UNWEIGHTED {
            @Override
            void add(Statistic lhs, double displacement, double time) {
                lhs.numerator += displacement / time;
                lhs.denominator += 1;
            }

            @Override
            String getName() { return "unweighted"; }
        };

        abstract void add(Statistic lhs, double displacement, double time);

        abstract String getName();
    }

    private class Statistic {

        double numerator;
        double denominator;

        Statistic() {
            this.numerator = 0.0;
            this.denominator = 0.0;
        }
    }
    
    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        @Override
        public String getParserName() {
            return CONTINUOUS_DIFFUSION_STATISTIC;
        }


        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            TreeDataLikelihood likelihood = (TreeDataLikelihood) xo.getChild(TreeDataLikelihood.class);

            if (!(likelihood.getDataLikelihoodDelegate() instanceof ContinuousDataLikelihoodDelegate)) {
                throw new XMLParseException("Must provide a continuous trait data likelihood");
            }

            String name = xo.getAttribute(NAME, xo.getId());
            String traitName = xo.getStringAttribute(TRAIT_NAME);

            TreeTrait.DA trait = (TreeTrait.DA) likelihood.getTreeTrait(traitName);
            if (trait == null) {
                throw new XMLParseException("Not trait `" + trait + "' in likelihood `" + likelihood.getId() + "`");
            }

            WeightingScheme weightingScheme = parseWeightingScheme(xo);
            DisplacementScheme displacementScheme = parseDisplacementScheme(xo);

            return new TreeDataContinuousDiffusionStatistic(
                    name,
                    trait,
                    likelihood,
                    weightingScheme,
                    displacementScheme);
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "A statistic that returns the average of the branch diffusion rates";
        }

        public Class getReturnType() {
            return TreeStatistic.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
                AttributeRule.newStringRule(NAME, true),
                AttributeRule.newStringRule(TRAIT_NAME),
                new ElementRule(TreeDataLikelihood.class),
                AttributeRule.newStringRule(WEIGHT, true),
                AttributeRule.newStringRule(DISPLACEMENT_SCHEME, true),
        };

        WeightingScheme parseWeightingScheme(XMLObject xo) throws XMLParseException {

            String name = xo.getAttribute(WEIGHT, WeightingScheme.WEIGHTED.getName());

            for (WeightingScheme scheme : WeightingScheme.values()) {
                if (name.compareToIgnoreCase(scheme.getName()) == 0) {
                    return scheme;
                }
            }

            throw new XMLParseException("Unknown weighting scheme '" + name + "'");
        }

        DisplacementScheme parseDisplacementScheme(XMLObject xo) throws XMLParseException {

            String name = xo.getAttribute(DISPLACEMENT_SCHEME, DisplacementScheme.QUADRATIC.getName());

            for (DisplacementScheme scheme : DisplacementScheme.values()) {
                if (name.compareToIgnoreCase(scheme.getName()) == 0) {
                    return scheme;
                }
            }
            throw new XMLParseException("Unknown displacement scheme '" + name + "'");
        }

    };
}

