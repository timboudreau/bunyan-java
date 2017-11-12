status = debug
dest = err
name = BunyanConfig

property.filename = target/rolling/rollingtest.log

filter.threshold.type = ThresholdFilter
filter.threshold.level = debug

appender.bunyan.type = Bunyan
appender.bunyan.name = Bunyan
appender.bunyan.layout.type = JsonLayout

appender.console.filter.threshold.type = ThresholdFilter
appender.console.filter.threshold.level = info
