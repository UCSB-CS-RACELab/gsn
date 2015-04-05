# SmartFarm Virtual Sensors

These virtual sensors have been created to use for the SmartFarm project. They each have some associated sample data for testing.
They can be found under `virtual-sensors/samples/`

## Dunning Ranch Energy

Location: a vineyard in Paso Robles, CA

Data file: Raw_data_dunning.csv

## Home Energy
This energy sensor is nicknamed "rainforest"

Location: Chandra's Home

Data file: home-energy.data

**Note**: The stream query on this virtual sensor is customized to only select the rows where the label is equal to
"InstantaneousDemand" (using postgres syntax). This type of customization cannot be made via the GSN-Assistant.

