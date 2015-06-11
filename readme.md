# Script CI API

Java 8 server to manage script saving & running

## Directory structure

```
root/
|-- jobs/
    |-- 1/
        |-- config.json
        |-- scripts/
            |-- script-o.sh
            |-- script-oe.sh
            |-- script-oo.sh
        |-- work/
            |-- [source files]
        |-- runs/
            |-- 1/
                |-- out.log
                |-- status.json
            |-- 2/
                |-- out.log
                |-- status.json
    |-- 2/...
    |-- single-job/
        |-- scripts/script.sh
        |-- work/
        |-- out.log
        |-- status.json
```

## To do...
- output streaming
- ui bundling