/*
 * GMRFSkyrideGradient.java
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

package dr.evomodel.coalescent;

import dr.evomodel.treedatalikelihood.discrete.NodeHeightTransform;
import dr.inference.hmc.GradientWrtParameterProvider;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.math.MultivariateFunction;
import dr.math.NumericalDerivative;
import dr.xml.Reportable;

/**
 * @author Marc A. Suchard
 * @author Xiang Ji
 */
public class GMRFSkyrideGradient implements GradientWrtParameterProvider, Reportable {

    protected GMRFSkyrideLikelihood skyrideLikelihood;
    private WrtParameter wrtParameter;
    private Parameter parameter;
    final private OldAbstractCoalescentLikelihood.IntervalNodeMapping intervalNodeMapping;
    final NodeHeightTransform nodeHeightTransform;

    public GMRFSkyrideGradient(GMRFSkyrideLikelihood gmrfSkyrideLikelihood,
                               WrtParameter wrtParameter,
                               Parameter parameter,
                               NodeHeightTransform nodeHeightTransform) {

        this.skyrideLikelihood = gmrfSkyrideLikelihood;
        this.intervalNodeMapping = skyrideLikelihood.getIntervalNodeMapping();
        this.wrtParameter = wrtParameter;
        this.nodeHeightTransform = nodeHeightTransform;
        this.parameter = parameter;
    }


    @Override
    public Likelihood getLikelihood() {
        return skyrideLikelihood;
    }

    @Override
    public Parameter getParameter() {
        return parameter;
    }

    @Override
    public int getDimension() {
        return getParameter().getDimension();
    }

    @Override
    public double[] getGradientLogDensity() {
        return wrtParameter.getGradientLogDensity(skyrideLikelihood, intervalNodeMapping);
    }

    private MultivariateFunction numeric1 = new MultivariateFunction() {
        @Override
        public double evaluate(double[] argument) {

            for (int i = 0; i < argument.length; ++i) {
                getParameter().setParameterValue(i, argument[i]);
            }

            skyrideLikelihood.makeDirty();
            return skyrideLikelihood.getLogLikelihood();
        }

        @Override
        public int getNumArguments() {
            return getParameter().getDimension();
        }

        @Override
        public double getLowerBound(int n) {
            return 0;
        }

        @Override
        public double getUpperBound(int n) {
            return Double.POSITIVE_INFINITY;
        }
    };

    @Override
    public String getReport() {
        double[] savedValues = getParameter().getParameterValues();
        double[] testGradient = NumericalDerivative.gradient(numeric1, getParameter().getParameterValues());
        for (int i = 0; i < savedValues.length; ++i) {
            getParameter().setParameterValue(i, savedValues[i]);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Peeling: ").append(new dr.math.matrixAlgebra.Vector(getGradientLogDensity()));
        sb.append("\n");
        sb.append("numeric: ").append(new dr.math.matrixAlgebra.Vector(testGradient));
        sb.append("\n");

        return sb.toString();
    }

    public enum WrtParameter {

        COALESCENT_INTERVAL {
            @Override
            double[] getGradientLogDensity(GMRFSkyrideLikelihood skyrideLikelihood,
                                           OldAbstractCoalescentLikelihood.IntervalNodeMapping intervalNodeMapping) {
                double[] unSortedNodeHeightGradient = super.getGradientLogDensityWrtUnsortedNodeHeight(skyrideLikelihood);
                double[] intervalGradient = new double[unSortedNodeHeightGradient.length];
                double accumulatedGradient = 0.0;
                for (int i = unSortedNodeHeightGradient.length - 1; i > -1; i--) {
                    accumulatedGradient += unSortedNodeHeightGradient[i];
                    intervalGradient[i] = accumulatedGradient;
                }
                return intervalGradient;
            }
        },

        NODE_HEIGHTS {
            @Override
            double[] getGradientLogDensity(GMRFSkyrideLikelihood skyrideLikelihood,
                                           OldAbstractCoalescentLikelihood.IntervalNodeMapping intervalNodeMapping) {
                double[] unSortedNodeHeightGradient = getGradientLogDensityWrtUnsortedNodeHeight(skyrideLikelihood);
                double[] sortedNodeHeightGradient = intervalNodeMapping.sortByNodeNumbers(unSortedNodeHeightGradient);
                return sortedNodeHeightGradient;
            }
        };

        abstract double[] getGradientLogDensity(GMRFSkyrideLikelihood skyrideLikelihood,
                                                OldAbstractCoalescentLikelihood.IntervalNodeMapping intervalNodeMapping);

        double[] getGradientLogDensityWrtUnsortedNodeHeight(GMRFSkyrideLikelihood skyrideLikelihood) {
            double[] unSortedNodeHeightGradient = new double[skyrideLikelihood.getCoalescentIntervalDimension()];
            double[] gamma = skyrideLikelihood.getPopSizeParameter().getParameterValues();

            int index = 0;
            for (int i = 0; i < skyrideLikelihood.getIntervalCount(); i++) {
                if (skyrideLikelihood.getIntervalType(i) == OldAbstractCoalescentLikelihood.CoalescentEventType.COALESCENT) {
                    double weight = -Math.exp(-gamma[index]) * skyrideLikelihood.getLineageCount(i) * (skyrideLikelihood.getLineageCount(i) - 1);
                    if (index < skyrideLikelihood.getCoalescentIntervalDimension() - 1) {
                        weight -= -Math.exp(-gamma[index + 1]) * skyrideLikelihood.getLineageCount(i + 1) * (skyrideLikelihood.getLineageCount(i + 1) - 1);
                    }
                    unSortedNodeHeightGradient[index] = weight / 2.0;
                    index++;
                }
            }
            return unSortedNodeHeightGradient;
        };



    }

}