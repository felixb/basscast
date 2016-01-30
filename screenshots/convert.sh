#! /bin/bash

for img in screenshot_*.png ; do
  convert ${img} -resize 25% small_${img}
done
