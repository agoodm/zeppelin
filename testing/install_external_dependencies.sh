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
MINICONDA_DIR="$HOME/miniconda"

# Install R dependencies if R profiles are used
if [[ ${PROFILE/"-Pr "} != $PROFILE ]] || [[ ${PROFILE/"-Psparkr "} != $PROFILE ]] ; then
  echo "R_LIBS=~/R" > ~/.Renviron
  echo "export R_LIBS=~/R" >> ~/.environ
  source ~/.environ
  if [[ ! -d "$HOME/R/knitr" ]] ; then
    mkdir -p ~/R
    R -e "install.packages('knitr', repos = 'http://cran.us.r-project.org', lib='~/R')"
  fi
fi

# Install Python dependencies for Python specific tests
if [[ -n "$PYTHON" ]] ; then
  if [ -d "$MINICONDA_DIR" ] && [ -e "$MINICONDA_DIR/bin/conda" ] ; then
    echo "Using cached miniconda installation"
  else
    echo "Using fresh miniconda installation"
    wget https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh -O miniconda.sh
    bash miniconda.sh -b -p $MINICONDA_DIR
    hash -r
    conda config --set always_yes yes --set changeps1 no
    conda update -q conda
    conda info -a
    conda config --add channels conda-forge
    conda create -q -n $PYTHON python=$PYTHON matplotlib pandasql
  fi
  
  echo "export PATH='$MINICONDA_DIR/envs/$PYTHON/bin:$PATH'" >> ~/.environ
  source ~/.environ
fi
