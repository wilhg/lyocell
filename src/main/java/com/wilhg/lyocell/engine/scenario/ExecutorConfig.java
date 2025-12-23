package com.wilhg.lyocell.engine.scenario;

import java.time.Duration;

public sealed interface ExecutorConfig permits 
    PerVuIterationsConfig, 
    SharedIterationsConfig, 
    ConstantVusConfig, 
    RampingVusConfig, 
    ConstantArrivalRateConfig {
    String type();
}
