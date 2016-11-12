#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Script for installing R / Python dependencies for Travis CI
set -ev
touch ~/.environ

contains() {
    string="$1"
    substring="$2"
    if test "${string#*$substring}" != "$string"
    then
        return 0    # $substring is in $string
    else
        return 1    # $substring is not in $string
    fi
}

# Install R dependencies if R profiles are used
if [[ contains "PROFILE" "-Pr " || contains "$PROFILE" "-Psparkr " ]] ; then
  mkdir -p ~/R
  echo "R_LIBS=~/R" > ~/.Renviron
  echo "export R_LIBS=~/R" >> ~/.environ
  source ~/.environ
  R -e "install.packages('knitr', repos = 'http://cran.us.r-project.org', lib='~/R')"
fi

# Install Python dependencies for Python specific tests
if [[ -v "$PYTHON" ]] ; then
  if [[ "$PYTHON" == "2.7" ]] ; then
    wget https://repo.continuum.io/miniconda/Miniconda2-latest-Linux-x86_64.sh -O miniconda.sh
  else
    wget https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -O miniconda.sh
  fi
  bash miniconda.sh -b -p $HOME/miniconda
  ech "export PATH='$HOME/miniconda/bin:$PATH'" >> ~/.environ
  source ~/.environ
  hash -r
  conda config --set always_yes yes --set changeps1 no
  conda update -q conda
  conda info -a
  conda config --add channels conda-forge
  conda install -q matplotlib pandasql
fi
