# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (c) 2013-2016, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.

# The Computer Language Benchmarks Game
# http://benchmarksgame.alioth.debian.org/
#
# originally by Kevin Carson
# modified by Tupteq, Fredrik Johansson, and Daniel Nanz
# modified by Maciej Fijalkowski
# 2to3

import sys, time

def combinations(l):
    result = []
    for x in range(len(l) - 1):
        ls = l[x+1:]
        for y in ls:
            result.append((l[x],y))
    return result

PI = 3.14159265358979323
SOLAR_MASS = 4 * PI * PI
DAYS_PER_YEAR = 365.24

BODIES = {
    'sun': ([0.0, 0.0, 0.0], [0.0, 0.0, 0.0], [SOLAR_MASS]),

    'jupiter': ([4.84143144246472090e+00,
                 -1.16032004402742839e+00,
                 -1.03622044471123109e-01],
                [1.66007664274403694e-03 * DAYS_PER_YEAR,
                 7.69901118419740425e-03 * DAYS_PER_YEAR,
                 -6.90460016972063023e-05 * DAYS_PER_YEAR],
                [9.54791938424326609e-04 * SOLAR_MASS]),

    'saturn': ([8.34336671824457987e+00,
                4.12479856412430479e+00,
                -4.03523417114321381e-01],
               [-2.76742510726862411e-03 * DAYS_PER_YEAR,
                4.99852801234917238e-03 * DAYS_PER_YEAR,
                2.30417297573763929e-05 * DAYS_PER_YEAR],
               [2.85885980666130812e-04 * SOLAR_MASS]),

    'uranus': ([1.28943695621391310e+01,
                -1.51111514016986312e+01,
                -2.23307578892655734e-01],
               [2.96460137564761618e-03 * DAYS_PER_YEAR,
                2.37847173959480950e-03 * DAYS_PER_YEAR,
                -2.96589568540237556e-05 * DAYS_PER_YEAR],
               [4.36624404335156298e-05 * SOLAR_MASS]),

    'neptune': ([1.53796971148509165e+01,
                 -2.59193146099879641e+01,
                 1.79258772950371181e-01],
                [2.68067772490389322e-03 * DAYS_PER_YEAR,
                 1.62824170038242295e-03 * DAYS_PER_YEAR,
                 -9.51592254519715870e-05 * DAYS_PER_YEAR],
                [5.15138902046611451e-05 * SOLAR_MASS]) }


SYSTEM = list(BODIES.values())
PAIRS = combinations(SYSTEM)


def advance(dt, n, bodies=SYSTEM, pairs=PAIRS):

    for w in range(n):
        for i in range(len(bodies)-1):
            ([x1, y1, z1], v1, m1) = bodies[i]
            for j in range(i+1,len(bodies)):
                ([x2, y2, z2], v2, m2) = bodies[j]
                dx = x1 - x2
                dy = y1 - y2
                dz = z1 - z2
                mag = dt * ((dx * dx + dy * dy + dz * dz) ** (-1.5))
                b1m = m1[0] * mag
                b2m = m2[0] * mag
                v1[0] -= dx * b2m
                v1[1] -= dy * b2m
                v1[2] -= dz * b2m
                v2[0] += dx * b1m
                v2[1] += dy * b1m
                v2[2] += dz * b1m
        for (r, [vx, vy, vz], m) in bodies:
            r[0] += dt * vx
            r[1] += dt * vy
            r[2] += dt * vz


def report_energy(bodies=SYSTEM, pairs=PAIRS, e=0.0):

    for (((x1, y1, z1), v1, m1),
         ((x2, y2, z2), v2, m2)) in pairs:
        dx = x1 - x2
        dy = y1 - y2
        dz = z1 - z2
        e -= (m1[0] * m2[0]) / ((dx * dx + dy * dy + dz * dz) ** 0.5)
    for (r, [vx, vy, vz], m) in bodies:
        e += m[0] * (vx * vx + vy * vy + vz * vz) / 2.
    print("%.9f" % e)

def offset_momentum(ref, bodies=SYSTEM, px=0.0, py=0.0, pz=0.0):

    for (r, [vx, vy, vz], m) in bodies:
        px -= vx * m[0]
        py -= vy * m[0]
        pz -= vz * m[0]
    (r, v, m) = ref
    v[0] = px / m[0]
    v[1] = py / m[0]
    v[2] = pz / m[0]

def main(n, ref='sun'):
    offset_momentum(BODIES[ref])
    report_energy()
    advance(0.01, n)
    report_energy()

# start = time.time()
if __name__ == '__main__':
    main(7)

# duration = "%.3f\n" % (time.time() - start)
# print("nbody: " + duration)
