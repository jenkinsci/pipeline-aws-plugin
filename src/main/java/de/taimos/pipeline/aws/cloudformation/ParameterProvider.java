package de.taimos.pipeline.aws.cloudformation;

import java.io.File;

public interface ParameterProvider {
    String[] getKeepParams();

    File getParamsFile();

    String[] getParams();

}
