#!/bin/bash
cd "/c/Users/basti/Documents/INV MOD"
git add .
git commit -m "Manuelles Backup $(date +"%Y-%m-%d_%H-%M-%S")"
git push 