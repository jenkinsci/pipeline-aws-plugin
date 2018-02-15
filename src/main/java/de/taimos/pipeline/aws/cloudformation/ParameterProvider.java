package de.taimos.pipeline.aws.cloudformation;

public interface ParameterProvider {
    String[] getKeepParams();

    String getParamsFile();

    String[] getParams();

}
