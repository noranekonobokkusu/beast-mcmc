/*
 * GradientWrtParameterProvider.java
 *
 * Copyright (c) 2002-2017 Alexei Drummond, Andrew Rambaut and Marc Suchard
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

package dr.inference.hmc;

import dr.inference.model.GradientProvider;
import dr.inference.model.Likelihood;
import dr.inference.model.Parameter;
import dr.inference.operators.hmc.NumericalHessianFromGradient;
import dr.math.MultivariateFunction;
import dr.math.NumericalDerivative;

import java.util.logging.Logger;

/**
 * @author Max Tolkoff
 * @author Marc A. Suchard
 */
public interface GradientWrtParameterProvider {

    Likelihood getLikelihood();

    Parameter getParameter();

    int getDimension();

    double[] getGradientLogDensity();

    class ParameterWrapper implements GradientWrtParameterProvider, HessianWrtParameterProvider{

        final GradientProvider provider;
        final Parameter parameter;
        final Likelihood likelihood;

        public ParameterWrapper(GradientProvider provider, Parameter parameter, Likelihood likelihood) {
            this.provider = provider;
            this.parameter = parameter;
            this.likelihood = likelihood;
        }

        @Override
        public Likelihood getLikelihood() {
            return likelihood;
        }

        @Override
        public Parameter getParameter() {
            return parameter;
        }

        @Override
        public int getDimension() {
            return parameter.getDimension();
        }

        @Override
        public double[] getGradientLogDensity() {
            return provider.getGradientLogDensity(parameter.getParameterValues());
        }

        @Override
        public double[] getDiagonalHessianLogDensity() {

            NumericalHessianFromGradient hessianFromGradient = new NumericalHessianFromGradient(this);
            return hessianFromGradient.getDiagonalHessianLogDensity();
        }

        @Override
        public double[][] getHessianLogDensity() {
            throw new RuntimeException("Not yet implemented");
        }
    }

    class GradientMismatchException extends Exception { }

    class CheckGradientNumerically {

        private final GradientWrtParameterProvider provider;
        private final Parameter parameter;
        private final double lowerBound;
        private final double upperBound;

        private final boolean checkValues;
        private final double tolerance;

        public CheckGradientNumerically(GradientWrtParameterProvider provider,
                                        double lowerBound, double upperBound,
                                        Double nullableTolerance) {
            this.provider = provider;
            this.parameter = provider.getParameter();
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;

            this.checkValues = nullableTolerance != null;
            this.tolerance = checkValues ? nullableTolerance : 0.0;
        }


        private MultivariateFunction numeric = new MultivariateFunction() {

            @Override
            public double evaluate(double[] argument) {

                setParameter(argument);
                return provider.getLikelihood().getLogLikelihood();
            }

            @Override
            public int getNumArguments() {
                return parameter.getDimension();
            }

            @Override
            public double getLowerBound(int n) {
                return lowerBound;
            }

            @Override
            public double getUpperBound(int n) {
                return upperBound;
            }
        };

        private void setParameter(double[] values) {

            for (int i = 0; i < values.length; ++i) {
                parameter.setParameterValueQuietly(i, values[i]);
            }

            parameter.fireParameterChangedEvent();
        }

        private double[] getNumericalGradient() {

            double[] savedValues = parameter.getParameterValues();
            double[] testGradient = NumericalDerivative.gradient(numeric, parameter.getParameterValues());

            setParameter(savedValues);
            return testGradient;
        }

        public String getReport() throws GradientMismatchException {

            double[] analytic = provider.getGradientLogDensity();
            double[] numeric = getNumericalGradient();

            StringBuilder sb = new StringBuilder();
            sb.append("analytic: ").append(new dr.math.matrixAlgebra.Vector(analytic));
            sb.append("\n");
            sb.append("numeric : ").append(new dr.math.matrixAlgebra.Vector(numeric));

            if (checkValues) {
                for (int i = 0; i < analytic.length; ++i) {
                    if (Math.abs(analytic[i] - numeric[i]) > tolerance) {
                        Logger.getLogger("dr.inference.hmc").info(sb.toString());
                        throw new GradientMismatchException();
                    }
                }
            }

            return sb.toString();
        }
    }

    static String getReportAndCheckForError(GradientWrtParameterProvider provider,
                                            double lowerBound, double upperBound,
                                            Double nullableTolerance) {
        String report;
        try {
            report = new CheckGradientNumerically(provider,
                    lowerBound, upperBound,
                    nullableTolerance
            ).getReport();
        } catch (GradientMismatchException e) {
            throw new RuntimeException(e.getMessage());
        }

        return report;
    }

    Double tolerance = 1E-4;
}
