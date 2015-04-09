# SmartFarm Virtual Sensors

These virtual sensors have been created to use for the SmartFarm project. They each have some associated sample data for testing.
They can be found under `virtual-sensors/samples/`

## Dunning Ranch Energy

Location: a vineyard in Paso Robles, CA

Data file: Raw_data_dunning.csv

## Green Button Energy
Uses data files from [my parser](https://github.com/kjorg50/greenButtonParser) because the "csv" files that Southern California
Edison offers for download are not traditional csv files. They have lots of unnecessary metadata and spacing. Intended as a
temporary solution until companies like SCE and PG&E offer their Green Button data via an API.

Location: Any smart meter from Southern California Edison

Data file: green_button_energy.csv

**Note**: The stream query doesn't select the "quality" column, because all of the sample data we downloaded didn't have any
values for "quality."

## Home Energy
This energy sensor is nicknamed "rainforest"

Location: Chandra's Home

Data file: home-energy.data

**Note**: The stream query on this virtual sensor is customized to only select the rows where the label is equal to
"InstantaneousDemand" (using postgres syntax). This type of customization cannot be made via the GSN-Assistant.

## Pressure Chamber

Sensor data from the Nichols Ranch "Pressure Chamber" device

Location: Nichols Ranch, Hanford, CA

Data file: pressure_chamber.csv