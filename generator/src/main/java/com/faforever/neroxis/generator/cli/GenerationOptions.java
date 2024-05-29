package com.faforever.neroxis.generator.cli;

import lombok.Getter;
import picocli.CommandLine;

@Getter
public class GenerationOptions {
    @CommandLine.ArgGroup
    private VisibilityOptions visibilityOptions;
    @CommandLine.ArgGroup
    private CasualOptions casualOptions = new CasualOptions();
}