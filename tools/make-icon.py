#!/usr/bin/env python3
"""Generate the launcher icon silhouette: a filled cloud with the up-arrow cut out.

Prints the VectorDrawable pathData (108 dp adaptive canvas). --drawable-out writes
app/src/main/res/drawable/ic_launcher_foreground.xml, and --svg-out writes the
website icon (64-unit square showing the visible 72 dp viewport). Nothing is
written unless every check passes.

The outline is computed, then checked against the analytic shape:
  * cloud = union of three circles and a rectangle; hole = a 5.5-wide
    round-capped, round-joined stroke of the arrow;
  * the emitted path (parsed back from its own text, so a wrong arc flag
    fails) matches that shape on a 0.25 dp grid, away from edges;
  * everything lies inside the 66 dp safe-zone circle, the hole keeps at
    least 1 dp from the cloud's edge, and the cloud is centred.
Standard library only.

Regenerate (from the repository root; PUBLIC is the public checkout):
  python3 tools/make-icon.py \
    --drawable-out app/src/main/res/drawable/ic_launcher_foreground.xml \
    --svg-out PUBLIC/docs/icon.svg
Add --check to verify that both files still match without writing them.
"""

import argparse
import math
import re
import sys

# Variant C geometry, before the shift that centres it on the canvas.
SHIFT = (-3.0, 3.5)
BIG, LEFT, RIGHT = ((57, 49), 15), ((41, 57), 10), ((72, 56), 11)
RECT = (41, 47, 72, 67)  # x0, y0, x1, y1
STEM = ((57, 63), (57, 46))  # bottom, apex
ARMS = ((50, 53), (64, 53))  # left and right arm ends
R = 5.5 / 2  # arrow stroke half-width

SAFE_RADIUS = 33.0  # 66 dp safe zone around the canvas centre
CENTRE = 54.0
COLOR = "#18513C"
BACKGROUND = "#B5F1D1"


class CheckFailed(Exception):
  pass


def require(condition, message):
  """A check that python -O cannot strip."""
  if not condition:
    raise CheckFailed(message)


def shift(p):
  return (p[0] + SHIFT[0], p[1] + SHIFT[1])


BIG = (shift(BIG[0]), BIG[1])
LEFT = (shift(LEFT[0]), LEFT[1])
RIGHT = (shift(RIGHT[0]), RIGHT[1])
RECT = (*shift(RECT[:2]), *shift(RECT[2:]))
STEM = (shift(STEM[0]), shift(STEM[1]))
ARMS = (shift(ARMS[0]), shift(ARMS[1]))


def at(circle, degrees):
  (cx, cy), r = circle
  a = math.radians(degrees)
  return (cx + r * math.cos(a), cy + r * math.sin(a))


def angle(circle, p):
  (cx, cy), _ = circle
  return math.degrees(math.atan2(p[1] - cy, p[0] - cx)) % 360


def intersections(c1, c2):
  (x1, y1), r1 = c1
  (x2, y2), r2 = c2
  d = math.hypot(x2 - x1, y2 - y1)
  a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
  h = math.sqrt(r1 * r1 - a * a)
  mx, my = x1 + a * (x2 - x1) / d, y1 + a * (y2 - y1) / d
  return [(mx + h * (y2 - y1) / d, my - h * (x2 - x1) / d),
          (mx - h * (y2 - y1) / d, my + h * (x2 - x1) / d)]


# Segments: ("M", point) starts a contour, ("L", end) is a line, and arc() returns
# ("A", circle, end, sweep, start_deg, end_deg). Sweep 1 means increasing angle
# (clockwise on screen, y down); span() needs the two angles.
def arc(circle, start_deg, end_deg, sweep):
  return ("A", circle, at(circle, end_deg), sweep, start_deg, end_deg)


def cloud():
  x0, y0, x1, y1 = RECT
  require(abs(LEFT[0][1] + LEFT[1] - y1) < 1e-9 and abs(RIGHT[0][1] + RIGHT[1] - y1) < 1e-9,
          "the side circles must touch the base")
  # Where the big circle crosses the rectangle's top edge, on the left.
  top_left = (BIG[0][0] - math.sqrt(BIG[1] ** 2 - (y0 - BIG[0][1]) ** 2), y0)
  # The upper crossing of the big and right circles.
  q = min(intersections(BIG, RIGHT), key=lambda p: p[1])
  return [
      ("M", at(LEFT, 90)),  # base, bottom of the left circle
      arc(LEFT, 90, 180, 1),  # up the left side (two arcs: none is exactly 180°)
      arc(LEFT, 180, 270, 1),  # to the left circle's top, the rectangle's top-left corner
      ("L", top_left),  # short flat edge along the rectangle's top
      # Arcs are split at their extreme points: an arc near 180° has a centre that
      # rounding of its endpoints can move a lot, and the extremes become exact.
      arc(BIG, angle(BIG, top_left), 270, 1),
      arc(BIG, 270, angle(BIG, q), 1),
      arc(RIGHT, angle(RIGHT, q), 360, 1),
      arc(RIGHT, 0, 90, 1),
      ("L", at(LEFT, 90)),  # the flat base
  ]


def unit(a, b):
  d = math.hypot(b[0] - a[0], b[1] - a[1])
  return ((b[0] - a[0]) / d, (b[1] - a[1]) / d)


def add(p, v, k=1.0):
  return (p[0] + k * v[0], p[1] + k * v[1])


def hole():
  bottom, apex = STEM
  left, right = ARMS
  cap = lambda c: (c, R)
  dr, dl = unit(apex, right), unit(apex, left)
  # Normals: "in" faces the stem, "out" faces away from it.
  nr_in, nr_out = (-dr[1], dr[0]), (dr[1], -dr[0])
  nl_in, nl_out = (dl[1], -dl[0]), (-dl[1], dl[0])
  sx_r, sx_l = bottom[0] + R, bottom[0] - R

  def meet(edge_point, d, x):  # where an arm's inner edge crosses a stem side
    t = (x - edge_point[0]) / d[0]
    return add(edge_point, d, t)

  jr = meet(add(apex, nr_in, R), dr, sx_r)
  jl = meet(add(apex, nl_in, R), dl, sx_l)
  deg = lambda c, p: angle(cap(c), p)
  return [
      ("M", (sx_l, bottom[1])),
      arc(cap(bottom), 180, 90, 0),  # bottom cap, two quarter arcs
      arc(cap(bottom), 90, 0, 0),
      ("L", jr),
      ("L", add(right, nr_in, R)),
      arc(cap(right), deg(right, add(right, nr_in, R)), deg(right, add(right, dr, R)), 0),
      arc(cap(right), deg(right, add(right, dr, R)), deg(right, add(right, nr_out, R)), 0),
      ("L", add(apex, nr_out, R)),
      arc(cap(apex), deg(apex, add(apex, nr_out, R)), deg(apex, add(apex, nl_out, R)), 0),
      ("L", add(left, nl_out, R)),
      arc(cap(left), deg(left, add(left, nl_out, R)), deg(left, add(left, dl, R)), 0),
      arc(cap(left), deg(left, add(left, dl, R)), deg(left, add(left, nl_in, R)), 0),
      ("L", jl),
      ("L", (sx_l, bottom[1])),
  ]


def span(seg):
  _, _, _, sweep, a0, a1 = seg
  return (a1 - a0) % 360 if sweep else (a0 - a1) % 360


def fmt(v):
  s = f"{v:.2f}".rstrip("0").rstrip(".")
  return "0" if s == "-0" else s


def emit(contours, transform=lambda p: p, scale=1.0):
  out = []
  for contour in contours:
    for seg in contour:
      if seg[0] == "M":
        x, y = transform(seg[1])
        out.append(f"M{fmt(x)},{fmt(y)}")
      elif seg[0] == "L":
        x, y = transform(seg[1])
        out.append(f"L{fmt(x)},{fmt(y)}")
      else:
        (_, r), end, sweep = seg[1], seg[2], seg[3]
        large = 1 if span(seg) > 180 else 0
        x, y = transform(end)
        rr = fmt(r * scale)
        out.append(f"A{rr},{rr} 0,{large},{sweep} {fmt(x)},{fmt(y)}")
    out.append("Z")
  return "".join(out)


# --- Self-checks: parse the emitted text back and compare with the analytic shape.

def flatten(path_data, step_deg=1.0):
  """Polylines from M/L/A/Z path text, using the SVG endpoint arc rules."""
  tokens = re.findall(r"[MLAZ]|-?\d+(?:\.\d+)?", path_data)
  polys, cur, i = [], None, 0
  while i < len(tokens):
    cmd = tokens[i]
    i += 1
    if cmd == "M":
      cur = [(float(tokens[i]), float(tokens[i + 1]))]
      polys.append(cur)
      i += 2
    elif cmd == "L":
      cur.append((float(tokens[i]), float(tokens[i + 1])))
      i += 2
    elif cmd == "A":
      rx, _, _, large, sweep, x, y = (float(t) for t in tokens[i:i + 7])
      i += 7
      cur.extend(arc_points(cur[-1], (x, y), rx, int(large), int(sweep), step_deg))
    elif cmd == "Z":
      pass
  return polys


def arc_points(p0, p1, r, large, sweep, step_deg):
  dx, dy = (p0[0] - p1[0]) / 2, (p0[1] - p1[1]) / 2
  d2 = dx * dx + dy * dy
  r = max(r, math.sqrt(d2))  # SVG scales up a radius that is too small
  k = math.sqrt(max(0.0, (r * r - d2) / d2)) if d2 else 0.0
  if large == sweep:
    k = -k
  cx = (p0[0] + p1[0]) / 2 + k * dy
  cy = (p0[1] + p1[1]) / 2 - k * dx
  a0 = math.atan2(p0[1] - cy, p0[0] - cx)
  a1 = math.atan2(p1[1] - cy, p1[0] - cx)
  delta = a1 - a0
  if sweep and delta < 0:
    delta += 2 * math.pi
  if not sweep and delta > 0:
    delta -= 2 * math.pi
  n = max(2, int(abs(math.degrees(delta)) / step_deg))
  return [(cx + r * math.cos(a0 + delta * j / n), cy + r * math.sin(a0 + delta * j / n))
          for j in range(1, n + 1)]


def densify(poly, step=0.1):
  out = []
  for a, b in zip(poly, poly[1:] + poly[:1]):
    n = max(1, int(math.dist(a, b) / step))
    out.extend((a[0] + (b[0] - a[0]) * j / n, a[1] + (b[1] - a[1]) * j / n) for j in range(n))
  return out


def even_odd(polys, x, y):
  inside = False
  for poly in polys:
    for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1]):
      if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
        inside = not inside
  return inside


def seg_dist(p, a, b):
  ax, ay = b[0] - a[0], b[1] - a[1]
  t = max(0.0, min(1.0, ((p[0] - a[0]) * ax + (p[1] - a[1]) * ay) / (ax * ax + ay * ay)))
  return math.dist(p, (a[0] + t * ax, a[1] + t * ay))


def analytic(x, y):
  p = (x, y)
  x0, y0, x1, y1 = RECT
  in_cloud = (x0 <= x <= x1 and y0 <= y <= y1) or any(
      math.dist(p, c) <= r for c, r in (BIG, LEFT, RIGHT))
  bottom, apex = STEM
  in_arrow = any(seg_dist(p, apex, end) <= R for end in (bottom, *ARMS))
  return in_cloud and not in_arrow


def check(path_data):
  polys = flatten(path_data)
  require(len(polys) == 2, "expected a cloud contour and a hole contour")
  cloud_pts, hole_pts = densify(polys[0]), densify(polys[1])
  xs, ys = [p[0] for p in cloud_pts], [p[1] for p in cloud_pts]
  bounds = (min(xs), min(ys), max(xs), max(ys))
  for got, want in zip(bounds, (28, 37.5, 80, 70.5)):
    require(abs(got - want) <= 0.01, f"cloud bounds {bounds}")
  require(abs((bounds[0] + bounds[2]) / 2 - CENTRE) <= 0.01, "cloud is not centred horizontally")
  require(abs((bounds[1] + bounds[3]) / 2 - CENTRE) <= 0.01, "cloud is not centred vertically")
  far = max(math.hypot(x - CENTRE, y - CENTRE) for x, y in cloud_pts + hole_pts)
  require(far <= SAFE_RADIUS, f"outside the safe zone: {far:.2f}")
  margin = min(math.dist(h, c) for h in hole_pts[::3] for c in cloud_pts)
  require(margin >= 1.0, f"hole margin {margin:.2f}")
  ring = [(0.1 * math.cos(k * math.pi / 4), 0.1 * math.sin(k * math.pi / 4)) for k in range(8)]
  checked = 0
  for i in range(int(20 / 0.25), int(88 / 0.25) + 1):
    for j in range(int(20 / 0.25), int(88 / 0.25) + 1):
      x, y = i * 0.25 + 0.0123, j * 0.25 + 0.0071  # off-grid jitter avoids vertex ties
      want = analytic(x, y)
      if any(analytic(x + dx, y + dy) != want for dx, dy in ring):
        continue  # within 0.1 dp of an edge
      require(even_odd(polys, x, y) == want, f"shape mismatch at ({x:.2f}, {y:.2f})")
      checked += 1
  require(checked >= 70000, f"only {checked} grid points were compared")
  return far, margin, checked


def website_svg():
  scale = 64 / 72
  to_site = lambda p: ((p[0] - 18) * scale, (p[1] - 18) * scale)
  data = emit([cloud(), hole()], to_site, scale)
  xs = [p[0] for p in densify(flatten(data)[0])]
  ys = [p[1] for p in densify(flatten(data)[0])]
  for got, want in zip((min(xs), min(ys), max(xs), max(ys)), (8.89, 17.33, 55.11, 46.67)):
    require(abs(got - want) <= 0.02, "website cloud bounds")
  return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">'
          f'<rect width="64" height="64" rx="18" fill="{BACKGROUND}"/>'
          f'<path fill="{COLOR}" fill-rule="evenodd" d="{data}"/></svg>\n')


DRAWABLE = """<!-- Generated by tools/make-icon.py; do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{data}"/>
</vector>
"""


def main():
  parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
  parser.add_argument("--drawable-out", help="write ic_launcher_foreground.xml to this path")
  parser.add_argument("--svg-out", help="write the website icon SVG to this path")
  parser.add_argument("--check", action="store_true",
                      help="compare the given files with the generated ones instead of writing")
  args = parser.parse_args()
  try:
    path_data = emit([cloud(), hole()])
    far, margin, checked = check(path_data)
    outputs = {}  # built in full before any file is opened
    if args.drawable_out:
      outputs[args.drawable_out] = DRAWABLE.format(color=COLOR, data=path_data)
    if args.svg_out:
      outputs[args.svg_out] = website_svg()
  except CheckFailed as e:
    sys.exit(f"make-icon: check failed: {e}")
  for path, text in outputs.items():
    if args.check:
      try:
        with open(path, encoding="utf-8") as f:
          current = f.read()
      except FileNotFoundError:
        current = None
      if current != text:
        sys.exit(f"make-icon: {path} differs from the generated icon; regenerate it")
    else:
      with open(path, "w", encoding="utf-8") as f:
        f.write(text)
  print(path_data)
  print(f"checks passed: {checked} grid points, max radius {far:.2f} <= 33, "
        f"hole margin {margin:.2f} dp" + (f", {len(outputs)} file(s) up to date" if args.check else ""),
        file=sys.stderr)


if __name__ == "__main__":
  main()
