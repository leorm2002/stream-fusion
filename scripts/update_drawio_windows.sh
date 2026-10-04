#!/bin/bash

find ./relazione/drawio -name *.drawio -exec rm -f {}.pdf \; -exec draw.io/draw.io  --crop -x -o {}.pdf {} \;

