package dr.inference.model;

public class ScaledMatrixParameter extends CompoundParameter implements MatrixParameterInterface, VariableListener {

    private final MatrixParameterInterface matrixParameter;
    private final Parameter scaleParameter;
    private final double[][] columnBuffers;
    private Boolean valuesKnown = false;

    public ScaledMatrixParameter(String name, MatrixParameterInterface matrixParameter, Parameter scaleParameter) {
        super(name, new Parameter[]{matrixParameter, scaleParameter});

        this.matrixParameter = matrixParameter;
        this.scaleParameter = scaleParameter;
        this.columnBuffers = new double[matrixParameter.getColumnDimension()][matrixParameter.getRowDimension()];

        addParameter(matrixParameter);
        addParameter(scaleParameter);
    }

    private void updateBuffer() {
        if (!valuesKnown) {
            for (int col = 0; col < matrixParameter.getColumnDimension(); col++) {
                for (int row = 0; row < matrixParameter.getRowDimension(); row++) {

                    columnBuffers[col][row] = matrixParameter.getParameterValue(row, col) *
                            scaleParameter.getParameterValue(col);
                }
            }

            valuesKnown = true;
        }
    }

    public MatrixParameterInterface getMatrixParameter() {
        return matrixParameter;
    }

    public Parameter getScaleParameter() {
        return scaleParameter;
    }

    @Override
    public void setParameterValue(int row, int col, double value) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setParameterValueQuietly(int row, int col, double value) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setParameterValueNotifyChangedAll(int row, int col, double value) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public double[] getColumnValues(int col) {
        updateBuffer();
        return columnBuffers[col].clone();
    }

    @Override
    public double[][] getParameterAsMatrix() {
        updateBuffer();
        return columnBuffers.clone();
    }

    @Override
    public int getColumnDimension() {
        return matrixParameter.getColumnDimension();
    }

    @Override
    public int getRowDimension() {
        return matrixParameter.getRowDimension();
    }

    @Override
    public int getUniqueParameterCount() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Parameter getUniqueParameter(int index) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void copyParameterValues(double[] destination, int offset) {
        updateBuffer();
        int newOffset = offset;
        for (int i = 0; i < matrixParameter.getColumnDimension(); i++) {
            System.arraycopy(columnBuffers[i], 0, destination, newOffset, matrixParameter.getRowDimension());
            newOffset += matrixParameter.getRowDimension();
        }
    }

    @Override
    public void setAllParameterValuesQuietly(double[] values, int offset) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String toSymmetricString() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isConstrainedSymmetric() {
        return false;
    }

    @Override
    public void variableChangedEvent(Variable variable, int index, ChangeType type) {
        super.variableChangedEvent(variable, index, type);
        valuesKnown = false; //TODO: do only update necessary indices
    }
}
