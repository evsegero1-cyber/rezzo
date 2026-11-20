#!/usr/bin/env python3
"""
Генератор УЧЕБНОЙ ЗАГЛУШКИ 3D-модели Chevrolet Rezzo (1.6, МКПП-5, передний привод).

ВАЖНО: это не настоящая модель Rezzo. Габариты взяты из паспортных данных
(длина 4350, ширина 1755, высота 1580, база 2600 мм, шины 185/70 R14),
а форма кузова и размещение агрегатов — условные. Заглушка нужна, чтобы
работали все механики приложения (вращение, двери, капот, свет, разборка,
выбор деталей), пока нет настоящей модели. Настоящую модель можно положить
вместо этой, сохранив имена узлов (см. docs/ASSETS.md).

Система координат (как в glTF): Y — вверх, X — вперёд по ходу машины,
Z — влево (сторона водителя, левый руль). Единицы — метры.

Запуск:  python3 tools/gen_placeholder_car.py
Результат:
  app/src/main/assets/models/rezzo_placeholder.glb
  app/src/main/assets/scene/car_scene.json
"""
import json
import math
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_GLB = os.path.join(ROOT, "app/src/main/assets/models/rezzo_placeholder.glb")
OUT_CFG = os.path.join(ROOT, "app/src/main/assets/scene/car_scene.json")

# ---------------------------------------------------------------- геометрия


def _norm(v):
    l = math.sqrt(v[0] ** 2 + v[1] ** 2 + v[2] ** 2) or 1.0
    return (v[0] / l, v[1] / l, v[2] / l)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


class Mesh:
    def __init__(self):
        self.pos = []
        self.nrm = []
        self.idx = []

    def add_tri(self, a, b, c, n=None):
        if n is None:
            n = _norm(_cross(_sub(b, a), _sub(c, a)))
        base = len(self.pos)
        self.pos += [a, b, c]
        self.nrm += [n, n, n]
        self.idx += [base, base + 1, base + 2]

    def add_quad(self, a, b, c, d, n=None):
        self.add_tri(a, b, c, n)
        self.add_tri(a, c, d, n)

    def merge(self, other):
        base = len(self.pos)
        self.pos += other.pos
        self.nrm += other.nrm
        self.idx += [i + base for i in other.idx]
        return self


def _area2(poly):
    s = 0.0
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        s += x1 * y2 - x2 * y1
    return s


def _point_in_tri(p, a, b, c):
    def sign(p1, p2, p3):
        return (p1[0] - p3[0]) * (p2[1] - p3[1]) - (p2[0] - p3[0]) * (p1[1] - p3[1])
    d1, d2, d3 = sign(p, a, b), sign(p, b, c), sign(p, c, a)
    neg = d1 < 0 or d2 < 0 or d3 < 0
    pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (neg and pos)


def triangulate(poly):
    """Ear clipping для простого многоугольника (CCW). Возвращает тройки индексов."""
    idx = list(range(len(poly)))
    tris = []
    guard = 0
    while len(idx) > 3 and guard < 10000:
        guard += 1
        ear_found = False
        for k in range(len(idx)):
            i0, i1, i2 = idx[k - 1], idx[k], idx[(k + 1) % len(idx)]
            a, b, c = poly[i0], poly[i1], poly[i2]
            cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if cross <= 1e-12:
                continue
            if any(_point_in_tri(poly[j], a, b, c) for j in idx if j not in (i0, i1, i2)):
                continue
            tris.append((i0, i1, i2))
            idx.pop(k)
            ear_found = True
            break
        if not ear_found:
            break
    if len(idx) == 3:
        tris.append(tuple(idx))
    return tris


def extrude_xy(poly, z0, z1):
    """Профиль в плоскости XY, вытянутый вдоль Z от z0 до z1."""
    poly = [tuple(p) for p in poly]
    if _area2(poly) < 0:
        poly = poly[::-1]
    m = Mesh()
    for (i0, i1, i2) in triangulate(poly):
        a, b, c = poly[i0], poly[i1], poly[i2]
        m.add_tri((a[0], a[1], z1), (b[0], b[1], z1), (c[0], c[1], z1), (0, 0, 1))
        m.add_tri((a[0], a[1], z0), (c[0], c[1], z0), (b[0], b[1], z0), (0, 0, -1))
    n = len(poly)
    for i in range(n):
        p, q = poly[i], poly[(i + 1) % n]
        dx, dy = q[0] - p[0], q[1] - p[1]
        nn = _norm((dy, -dx, 0))
        m.add_quad((p[0], p[1], z0), (q[0], q[1], z0), (q[0], q[1], z1), (p[0], p[1], z1), nn)
    return m


def box(x0, x1, y0, y1, z0, z1):
    return extrude_xy([(x0, y0), (x1, y0), (x1, y1), (x0, y1)], z0, z1)


def strip(p0, p1, width):
    """Полоса толщиной width вдоль отрезка p0-p1 в плоскости XY (многоугольник)."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    l = math.hypot(dx, dy) or 1.0
    ox, oy = -dy / l * width / 2, dx / l * width / 2
    return [(p0[0] - ox, p0[1] - oy), (p1[0] - ox, p1[1] - oy), (p1[0] + ox, p1[1] + oy), (p0[0] + ox, p0[1] + oy)]


def _basis(axis):
    w = _norm(axis)
    helper = (0, 1, 0) if abs(w[1]) < 0.9 else (1, 0, 0)
    u = _norm(_cross(helper, w))
    v = _cross(w, u)
    return u, v, w


def cylinder(p0, p1, r, seg=20, caps=True):
    u, v, w = _basis(_sub(p1, p0))
    m = Mesh()
    ring0, ring1, nrm = [], [], []
    for i in range(seg):
        a = 2 * math.pi * i / seg
        d = _add(_mul(u, math.cos(a)), _mul(v, math.sin(a)))
        ring0.append(_add(p0, _mul(d, r)))
        ring1.append(_add(p1, _mul(d, r)))
        nrm.append(d)
    for i in range(seg):
        j = (i + 1) % seg
        base = len(m.pos)
        m.pos += [ring0[i], ring0[j], ring1[j], ring1[i]]
        m.nrm += [nrm[i], nrm[j], nrm[j], nrm[i]]
        m.idx += [base, base + 1, base + 2, base, base + 2, base + 3]
        if caps:
            m.add_tri(p1, ring1[i], ring1[j], w)
            m.add_tri(p0, ring0[j], ring0[i], _mul(w, -1))
    return m


def tube(center, axis, r_out, r_in, length, seg=28):
    """Кольцо (например шина): внешний и внутренний цилиндры + торцы."""
    u, v, w = _basis(axis)
    c0 = _add(center, _mul(w, -length / 2))
    c1 = _add(center, _mul(w, length / 2))
    m = Mesh()
    for i in range(seg):
        a0 = 2 * math.pi * i / seg
        a1 = 2 * math.pi * (i + 1) / seg
        d0 = _add(_mul(u, math.cos(a0)), _mul(v, math.sin(a0)))
        d1 = _add(_mul(u, math.cos(a1)), _mul(v, math.sin(a1)))
        o00, o01 = _add(c0, _mul(d0, r_out)), _add(c0, _mul(d1, r_out))
        o10, o11 = _add(c1, _mul(d0, r_out)), _add(c1, _mul(d1, r_out))
        i00, i01 = _add(c0, _mul(d0, r_in)), _add(c0, _mul(d1, r_in))
        i10, i11 = _add(c1, _mul(d0, r_in)), _add(c1, _mul(d1, r_in))
        base = len(m.pos)
        m.pos += [o00, o01, o11, o10]
        m.nrm += [d0, d1, d1, d0]
        m.idx += [base, base + 1, base + 2, base, base + 2, base + 3]
        nd0, nd1 = _mul(d0, -1), _mul(d1, -1)
        base = len(m.pos)
        m.pos += [i00, i10, i11, i01]
        m.nrm += [nd0, nd0, nd1, nd1]
        m.idx += [base, base + 1, base + 2, base, base + 2, base + 3]
        m.add_quad(i10, o10, o11, i11, w)
        m.add_quad(i00, i01, o01, o00, _mul(w, -1))
    return m


def torus(center, axis, R, r, seg=36, sides=10):
    u, v, w = _basis(axis)
    m = Mesh()
    grid = []
    for i in range(seg + 1):
        a = 2 * math.pi * i / seg
        d = _add(_mul(u, math.cos(a)), _mul(v, math.sin(a)))
        row = []
        for j in range(sides + 1):
            b = 2 * math.pi * j / sides
            n = _add(_mul(d, math.cos(b)), _mul(w, math.sin(b)))
            p = _add(_add(center, _mul(d, R)), _mul(n, r))
            row.append((p, n))
        grid.append(row)
    for i in range(seg):
        for j in range(sides):
            (p00, n00), (p01, n01) = grid[i][j], grid[i][j + 1]
            (p10, n10), (p11, n11) = grid[i + 1][j], grid[i + 1][j + 1]
            base = len(m.pos)
            m.pos += [p00, p10, p11, p01]
            m.nrm += [n00, n10, n11, n01]
            m.idx += [base, base + 1, base + 2, base, base + 2, base + 3]
    return m


def helix(p0, p1, R, wire, turns=6, seg_per_turn=14):
    """Витая пружина вокруг оси p0-p1."""
    u, v, w = _basis(_sub(p1, p0))
    length = math.dist(p0, p1)
    pts = []
    n = turns * seg_per_turn
    for i in range(n + 1):
        t = i / n
        a = 2 * math.pi * turns * t
        p = _add(_add(p0, _mul(w, length * t)), _add(_mul(u, R * math.cos(a)), _mul(v, R * math.sin(a))))
        pts.append(p)
    m = Mesh()
    for i in range(n):
        m.merge(cylinder(pts[i], pts[i + 1], wire, seg=6, caps=False))
    return m


def mirror_z(mesh):
    m = Mesh()
    for p in mesh.pos:
        m.pos.append((p[0], p[1], -p[2]))
    for n in mesh.nrm:
        m.nrm.append((n[0], n[1], -n[2]))
    for i in range(0, len(mesh.idx), 3):
        a, b, c = mesh.idx[i], mesh.idx[i + 1], mesh.idx[i + 2]
        m.idx += [a, c, b]
    return m


# ---------------------------------------------------------------- сцена glTF

class Scene:
    def __init__(self):
        self.nodes = []      # dict(name, translation, mesh, children)
        self.meshes = []     # (Mesh, material_index)
        self.materials = []
        self.by_name = {}

    def material(self, name, color, metallic=0.0, roughness=0.6, alpha=1.0, blend=False):
        mat = {
            "name": name,
            "pbrMetallicRoughness": {
                "baseColorFactor": [color[0], color[1], color[2], alpha],
                "metallicFactor": metallic,
                "roughnessFactor": roughness,
            },
            "doubleSided": True,
        }
        if blend:
            mat["alphaMode"] = "BLEND"
        self.materials.append(mat)
        return len(self.materials) - 1

    def node(self, name, parent=None, translation=(0, 0, 0)):
        assert name not in self.by_name, name
        n = {"name": name, "translation": list(translation), "children": []}
        self.nodes.append(n)
        idx = len(self.nodes) - 1
        self.by_name[name] = idx
        if parent is not None:
            self.nodes[self.by_name[parent]]["children"].append(idx)
        return idx

    def mesh_node(self, name, mesh, look, parent=None, translation=(0, 0, 0)):
        """look = (цвет, metallic, roughness, alpha, blend). У каждой детали свой материал."""
        color, metallic, rough, alpha, blend = look
        mat = self.material(name + "_mat", color, metallic, rough, alpha, blend)
        self.meshes.append((mesh, mat, name))
        idx = self.node(name, parent, translation)
        self.nodes[idx]["mesh"] = len(self.meshes) - 1
        return idx

    def write_glb(self, path):
        bin_data = bytearray()
        buffer_views, accessors, gl_meshes = [], [], []

        def add_view(data, target):
            while len(bin_data) % 4:
                bin_data.append(0)
            offset = len(bin_data)
            bin_data.extend(data)
            buffer_views.append({"buffer": 0, "byteOffset": offset, "byteLength": len(data), "target": target})
            return len(buffer_views) - 1

        for mesh, mat, name in self.meshes:
            pos = b"".join(struct.pack("<3f", *p) for p in mesh.pos)
            nrm = b"".join(struct.pack("<3f", *_norm(n)) for n in mesh.nrm)
            big = len(mesh.pos) > 65535
            ind = b"".join(struct.pack("<I" if big else "<H", i) for i in mesh.idx)
            pv = add_view(pos, 34962)
            nv = add_view(nrm, 34962)
            iv = add_view(ind, 34963)
            mn = [min(p[k] for p in mesh.pos) for k in range(3)]
            mx = [max(p[k] for p in mesh.pos) for k in range(3)]
            accessors.append({"bufferView": pv, "componentType": 5126, "count": len(mesh.pos), "type": "VEC3", "min": mn, "max": mx})
            pa = len(accessors) - 1
            accessors.append({"bufferView": nv, "componentType": 5126, "count": len(mesh.nrm), "type": "VEC3"})
            na = len(accessors) - 1
            accessors.append({"bufferView": iv, "componentType": 5125 if big else 5123, "count": len(mesh.idx), "type": "SCALAR"})
            ia = len(accessors) - 1
            gl_meshes.append({"name": name, "primitives": [{"attributes": {"POSITION": pa, "NORMAL": na}, "indices": ia, "material": mat}]})

        nodes = []
        for n in self.nodes:
            out = {"name": n["name"]}
            if any(abs(t) > 1e-9 for t in n["translation"]):
                out["translation"] = n["translation"]
            if n["children"]:
                out["children"] = n["children"]
            if "mesh" in n:
                out["mesh"] = n["mesh"]
            nodes.append(out)

        child_set = {c for n in self.nodes for c in n["children"]}
        roots = [i for i in range(len(self.nodes)) if i not in child_set]
        gltf = {
            "asset": {"version": "2.0", "generator": "rezzo-academy placeholder generator"},
            "extras": {"placeholder": True, "note": "Учебная заглушка: габариты по паспортным данным, форма условная"},
            "scene": 0,
            "scenes": [{"name": "rezzo", "nodes": roots}],
            "nodes": nodes,
            "meshes": gl_meshes,
            "materials": self.materials,
            "accessors": accessors,
            "bufferViews": buffer_views,
            "buffers": [{"byteLength": len(bin_data)}],
        }
        js = json.dumps(gltf, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        while len(js) % 4:
            js += b" "
        while len(bin_data) % 4:
            bin_data.append(0)
        total = 12 + 8 + len(js) + 8 + len(bin_data)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(struct.pack("<III", 0x46546C67, 2, total))
            f.write(struct.pack("<II", len(js), 0x4E4F534A))
            f.write(js)
            f.write(struct.pack("<II", len(bin_data), 0x004E4942))
            f.write(bin_data)
        return total


# ---------------------------------------------------------------- внешний вид

def look(c, metallic=0.0, rough=0.6, alpha=1.0, blend=False):
    return (c, metallic, rough, alpha, blend)


PAINT = look((0.30, 0.33, 0.37), 0.2, 0.35, 1.0, True)     # кузов: BLEND, чтобы работал режим «прозрачный кузов»
GLASS = look((0.05, 0.08, 0.10), 0.0, 0.05, 0.38, True)
BLACK_PLASTIC = look((0.025, 0.025, 0.028), 0.0, 0.7)
TRIM = look((0.05, 0.05, 0.055), 0.0, 0.6, 1.0, True)
CAST = look((0.32, 0.33, 0.34), 0.3, 0.55)
ALU = look((0.55, 0.56, 0.58), 0.3, 0.35)
DARK_METAL = look((0.12, 0.12, 0.13), 0.3, 0.5)
RUBBER = look((0.018, 0.018, 0.018), 0.0, 0.9)
RIM = look((0.55, 0.57, 0.60), 0.4, 0.3)
DISC = look((0.38, 0.38, 0.38), 0.4, 0.45)
CALIPER = look((0.20, 0.20, 0.22), 0.3, 0.5)
SPRING = look((0.10, 0.11, 0.13), 0.3, 0.45)
EXHAUST = look((0.28, 0.26, 0.24), 0.3, 0.65)
HOSE = look((0.03, 0.03, 0.03), 0.0, 0.8)
SEAT = look((0.07, 0.07, 0.08), 0.0, 0.85)
INTERIOR = look((0.09, 0.09, 0.10), 0.0, 0.7)
LENS_CLEAR = look((0.55, 0.56, 0.58), 0.0, 0.15)
LENS_RED = look((0.35, 0.03, 0.03), 0.0, 0.2)
LENS_AMBER = look((0.45, 0.22, 0.02), 0.0, 0.2)
RADIATOR = look((0.10, 0.11, 0.12), 0.2, 0.6)
BATTERY = look((0.04, 0.04, 0.045), 0.0, 0.6)
FILTER_BOX = look((0.06, 0.06, 0.065), 0.0, 0.6)
FUEL_TANK = look((0.10, 0.10, 0.11), 0.0, 0.7)
RESERVOIR = look((0.75, 0.75, 0.72), 0.0, 0.5)
GROUND = look((0.06, 0.065, 0.07), 0.0, 0.95)

# ---------------------------------------------------------------- размеры

L, W, H = 4.35, 1.755, 1.58           # паспортные габариты, м
WB = 2.60                             # колёсная база
XF, XR = 1.25, -1.35                  # оси (условный свес)
WHEEL_R = 0.307                       # 185/70 R14: (355.6 + 2*129.5)/2 мм
WHEEL_W = 0.185
TRACK_Z = 0.735                       # условная половина колеи
HALF_W = W / 2                        # 0.8775
SIDE_IN = 0.835                       # внутренняя плоскость боковины
BELT = 0.98                           # линия окон
ROOF = 1.56

s = Scene()
s.node("rezzo")


def side_pieces(side):
    """Боковины кузова одной стороны. side = +1 (левая) / -1 (правая)."""
    sfx = "l" if side > 0 else "r"
    pieces = []

    def add(name, poly, z0=SIDE_IN, z1=HALF_W, lk=PAINT, parent="rezzo", translation=(0, 0, 0)):
        m = extrude_xy(poly, z0, z1)
        if side < 0:
            m = mirror_z(m)
        s.mesh_node(f"{name}_{sfx}", m, lk, parent, translation)
        pieces.append(f"{name}_{sfx}")

    # переднее крыло с аркой колеса
    arch = [(XF + 0.37 * math.cos(a), 0.307 + 0.37 * math.sin(a)) for a in [math.pi * (1 - i / 14) for i in range(15)]]
    fender = [(0.86, 0.30)] + arch + [(2.12, 0.30), (2.15, 0.55), (2.13, 0.82), (1.06, 0.98), (0.86, 0.98)]
    add("fender_front", fender)
    add("pillar_a", [(1.07, 0.97), (0.22, 1.53), (0.13, 1.53), (0.96, 0.97)], 0.78, 0.86)
    add("glass_quarter_front", [(0.87, 0.99), (1.0, 0.99), (0.87, 1.08)], 0.842, 0.852, GLASS)
    add("pillar_b", [(-0.20, BELT), (-0.10, BELT), (-0.10, 1.52), (-0.20, 1.52)], 0.82, 0.865)
    add("pillar_c", [(-1.07, BELT), (-0.97, BELT), (-0.97, 1.52), (-1.07, 1.52)], 0.82, 0.865)
    rarch = [(XR + 0.36 * math.cos(a), 0.307 + 0.36 * math.sin(a)) for a in [math.pi * (1 - i / 14) for i in range(15)]]
    quarter = [(-2.16, 0.30)] + rarch + [(-0.97, 0.30), (-0.97, BELT), (-2.12, BELT)]
    add("quarter_rear", quarter)
    add("glass_quarter_rear", [(-1.09, 0.99), (-1.09, 1.45), (-1.83, 1.45), (-1.92, 0.99)], 0.842, 0.852, GLASS)
    add("pillar_d", [(-1.85, BELT), (-2.12, BELT), (-1.98, 1.53), (-1.86, 1.53)], 0.80, 0.865)
    add("roof_rail", [(0.22, 1.47), (-1.97, 1.47), (-1.97, 1.53), (0.22, 1.53)], 0.80, 0.865)
    add("sill", [(0.86, 0.20), (-0.97, 0.20), (-0.97, 0.28), (0.86, 0.28)], 0.80, 0.86, TRIM)
    return pieces


def door(name, side, x_front, x_rear, glass_poly, frame_segments, window_drop):
    sfx = "l" if side > 0 else "r"
    hinge = (x_front, 0.6, side * HALF_W)
    neg = (-hinge[0], -hinge[1], -hinge[2])
    pivot = f"{name}_{sfx}"
    s.node(pivot, "rezzo", hinge)

    def mk(poly, z0, z1):
        m = extrude_xy(poly, z0, z1)
        return mirror_z(m) if side < 0 else m

    s.mesh_node(f"{pivot}_panel", mk([(x_front, 0.28), (x_rear, 0.28), (x_rear, BELT), (x_front, BELT)], SIDE_IN, HALF_W), PAINT, pivot, neg)
    frame = Mesh()
    for (p0, p1) in frame_segments:
        frame.merge(extrude_xy(strip(p0, p1, 0.035), 0.84, 0.872))
    if side < 0:
        frame = mirror_z(frame)
    s.mesh_node(f"{pivot}_frame", frame, PAINT, pivot, neg)
    win = f"window_{name.split('_')[1]}_{sfx}"     # window_front_l / window_rear_l
    s.mesh_node(win, mk(glass_poly, 0.848, 0.856), GLASS, pivot, neg)
    handle = box(x_rear + 0.06, x_rear + 0.20, 0.86, 0.89, HALF_W, HALF_W + 0.018)
    if side < 0:
        handle = mirror_z(handle)
    s.mesh_node(f"{pivot}_handle", handle, BLACK_PLASTIC, pivot, neg)
    return pivot, win, window_drop


body_nodes = []
glass_nodes = []
for sd in (1, -1):
    body_nodes += side_pieces(sd)

doors = []
for sd in (1, -1):
    doors.append(door("door_front", sd, 0.86, -0.10,
                      [(0.84, 0.99), (0.84, 1.09), (0.24, 1.455), (-0.08, 1.455), (-0.08, 0.99)],
                      [((0.86, 1.10), (0.24, 1.475)), ((0.24, 1.475), (-0.10, 1.475)), ((-0.10, 0.98), (-0.10, 1.475))],
                      0.40))
    doors.append(door("door_rear", sd, -0.20, -0.97,
                      [(-0.22, 0.99), (-0.22, 1.455), (-0.95, 1.455), (-0.95, 0.99)],
                      [((-0.20, 1.475), (-0.97, 1.475)), ((-0.97, 0.98), (-0.97, 1.475)), ((-0.20, 0.98), (-0.20, 1.475))],
                      0.32))

# крыша, лобовое стекло, передок, бамперы
s.mesh_node("roof", extrude_xy([(0.20, 1.50), (-1.97, 1.50), (-1.97, 1.56), (0.12, 1.565)], -0.83, 0.83), PAINT, "rezzo")
s.mesh_node("windshield", extrude_xy(strip((1.06, 0.985), (0.19, 1.505), 0.012), -0.80, 0.80), GLASS, "rezzo")
s.mesh_node("cowl", extrude_xy([(1.12, 0.94), (1.12, 0.985), (0.98, 0.99), (0.98, 0.94)], -0.82, 0.82), TRIM, "rezzo")
s.mesh_node("front_panel", box(2.08, 2.15, 0.45, 0.76, -0.50, 0.50), BLACK_PLASTIC, "rezzo")
s.mesh_node("bumper_front", extrude_xy([(2.04, 0.22), (2.19, 0.24), (2.21, 0.50), (2.14, 0.56), (2.04, 0.56)], -0.86, 0.86), TRIM, "rezzo")
s.mesh_node("bumper_rear", extrude_xy([(-2.04, 0.22), (-2.19, 0.24), (-2.21, 0.50), (-2.15, 0.55), (-2.04, 0.55)], -0.86, 0.86), TRIM, "rezzo")
s.mesh_node("floor", box(-2.0, 1.0, 0.24, 0.29, -0.84, 0.84), DARK_METAL, "rezzo")
s.mesh_node("firewall", box(0.96, 1.02, 0.29, 0.95, -0.80, 0.80), DARK_METAL, "rezzo")
s.mesh_node("rear_panel", box(-2.15, -2.08, 0.29, 0.56, -0.80, 0.80), PAINT, "rezzo")
body_nodes += ["roof", "cowl", "bumper_front", "bumper_rear", "rear_panel", "front_panel"]
glass_nodes += ["windshield", "glass_quarter_front_l", "glass_quarter_front_r", "glass_quarter_rear_l", "glass_quarter_rear_r"]

# капот: шарнир у лобового стекла
HOOD_HINGE = (1.06, 0.975, 0.0)
s.node("hood", "rezzo", HOOD_HINGE)
s.mesh_node("hood_panel", extrude_xy(strip((2.13, 0.815), (1.06, 0.975), 0.03), -0.83, 0.83), PAINT, "hood",
            (-HOOD_HINGE[0], -HOOD_HINGE[1], 0))

# дверь багажника: шарнир сверху
TG_HINGE = (-1.96, 1.535, 0.0)
s.node("tailgate", "rezzo", TG_HINGE)
tg_neg = (-TG_HINGE[0], -TG_HINGE[1], 0)
s.mesh_node("tailgate_panel", extrude_xy([(-2.06, 0.98), (-2.115, 0.98), (-2.175, 0.56), (-2.12, 0.56)], -0.80, 0.80), PAINT, "tailgate", tg_neg)
tg_frame = extrude_xy(strip((-1.955, 1.525), (-2.0, 1.47), 0.04), -0.80, 0.80)
for zz in (1, -1):
    fr = extrude_xy(strip((-1.97, 1.50), (-2.085, 0.99), 0.05), 0.74, 0.80)
    tg_frame.merge(fr if zz > 0 else mirror_z(fr))
s.mesh_node("tailgate_frame", tg_frame, PAINT, "tailgate", tg_neg)
s.mesh_node("tailgate_glass", extrude_xy(strip((-1.975, 1.48), (-2.08, 0.99), 0.012), -0.74, 0.74), GLASS, "tailgate", tg_neg)
glass_nodes += ["tailgate_glass"]

# ---------------------------------------------------------------- светотехника
lights = {}
for sd in (1, -1):
    sfx = "l" if sd > 0 else "r"

    def lamp(name, bx, lk):
        m = bx if sd > 0 else mirror_z(bx)
        s.mesh_node(f"{name}_{sfx}", m, lk, "rezzo")
        return f"{name}_{sfx}"

    lights.setdefault("headlight", []).append(lamp("headlight", box(2.08, 2.145, 0.66, 0.79, 0.46, 0.82), LENS_CLEAR))
    lights.setdefault("turn_front", []).append(lamp("turn_front", box(2.07, 2.135, 0.59, 0.655, 0.62, 0.83), LENS_AMBER))
    lights.setdefault("taillight", []).append(lamp("taillight", box(-2.165, -2.115, 0.92, 1.16, 0.72, 0.86), LENS_RED))
    lights.setdefault("turn_rear", []).append(lamp("turn_rear", box(-2.16, -2.11, 0.82, 0.915, 0.72, 0.86), LENS_AMBER))
    lights.setdefault("reverse", []).append(lamp("reverse_light", box(-2.155, -2.105, 0.74, 0.815, 0.72, 0.86), LENS_CLEAR))

# ---------------------------------------------------------------- колёса и тормоза
wheels = []
for (wx, tag) in ((XF, "f"), (XR, "r")):
    for sd in (1, -1):
        sfx = tag + ("l" if sd > 0 else "r")
        c = (wx, WHEEL_R, sd * TRACK_Z)
        neg = (-c[0], -c[1], -c[2])
        s.node(f"wheel_{sfx}", "rezzo", c)
        s.mesh_node(f"wheel_{sfx}_tire", tube(c, (0, 0, 1), WHEEL_R, 0.185, WHEEL_W), RUBBER, f"wheel_{sfx}", neg)
        face_z = sd * (TRACK_Z + WHEEL_W / 2 - 0.035)
        rim = cylinder((wx, WHEEL_R, face_z - sd * 0.01), (wx, WHEEL_R, face_z + sd * 0.01), 0.18, seg=24)
        hub = cylinder((wx, WHEEL_R, face_z), (wx, WHEEL_R, face_z + sd * 0.025), 0.055, seg=16)
        s.mesh_node(f"wheel_{sfx}_rim", rim.merge(hub), RIM, f"wheel_{sfx}", neg)
        wheels.append(f"wheel_{sfx}")
        inner_z = sd * (TRACK_Z - 0.045)
        if tag == "f":
            s.mesh_node(f"brake_disc_{sfx}", cylinder((wx, WHEEL_R, inner_z - 0.012), (wx, WHEEL_R, inner_z + 0.012), 0.128, seg=28), DISC, "rezzo")
            cal = box(wx - 0.155, wx - 0.085, WHEEL_R + 0.02, WHEEL_R + 0.13, inner_z - 0.035, inner_z + 0.035)
            s.mesh_node(f"caliper_{sfx}", cal, CALIPER, "rezzo")
        else:
            s.mesh_node(f"brake_drum_{sfx}", cylinder((wx, WHEEL_R, inner_z - 0.03), (wx, WHEEL_R, inner_z + 0.03), 0.115, seg=28), DARK_METAL, "rezzo")

# ---------------------------------------------------------------- подвеска и рулевое (условная схема)
for sd in (1, -1):
    sfx = "l" if sd > 0 else "r"
    z = sd
    def mesh_side(m):
        return m
    strut = cylinder((XF - 0.02, 0.42, z * 0.66), (XF - 0.04, 0.95, z * 0.60), 0.032)
    s.mesh_node(f"strut_front_{sfx}", strut, DARK_METAL, "rezzo")
    s.mesh_node(f"spring_front_{sfx}", helix((XF - 0.025, 0.62, z * 0.645), (XF - 0.04, 0.92, z * 0.60), 0.075, 0.011, turns=5), SPRING, "rezzo")
    arm = cylinder((XF, 0.235, z * 0.66), (XF + 0.05, 0.235, z * 0.30), 0.022).merge(
        cylinder((XF, 0.235, z * 0.66), (XF - 0.30, 0.235, z * 0.34), 0.02))
    s.mesh_node(f"lower_arm_front_{sfx}", arm, DARK_METAL, "rezzo")
    s.mesh_node(f"tie_rod_{sfx}", cylinder((1.12, 0.37, z * 0.44), (XF - 0.13, 0.36, z * 0.64), 0.012), DARK_METAL, "rezzo")
    s.mesh_node(f"driveshaft_{sfx}", cylinder((1.33, 0.31, z * 0.10 + 0.20), (XF, WHEEL_R, z * 0.66), 0.018), DARK_METAL, "rezzo")
    # задняя подвеска: независимая (по данным для Tacuma), схема условная
    s.mesh_node(f"shock_rear_{sfx}", cylinder((XR - 0.02, 0.38, z * 0.64), (XR - 0.06, 0.88, z * 0.60), 0.028), DARK_METAL, "rezzo")
    s.mesh_node(f"spring_rear_{sfx}", helix((XR - 0.03, 0.42, z * 0.64), (XR - 0.055, 0.80, z * 0.605), 0.07, 0.010, turns=5), SPRING, "rezzo")
    links = cylinder((XR + 0.06, 0.25, z * 0.64), (XR + 0.06, 0.27, z * 0.12), 0.016).merge(
        cylinder((XR - 0.08, 0.25, z * 0.64), (XR - 0.08, 0.27, z * 0.12), 0.016)).merge(
        cylinder((XR + 0.02, 0.27, z * 0.66), (XR + 0.42, 0.30, z * 0.62), 0.02))
    s.mesh_node(f"links_rear_{sfx}", links, DARK_METAL, "rezzo")
s.mesh_node("steering_rack", cylinder((1.12, 0.37, -0.45), (1.12, 0.37, 0.45), 0.03), DARK_METAL, "rezzo")
s.mesh_node("stabilizer_front", cylinder((1.03, 0.30, -0.56), (1.03, 0.30, 0.56), 0.012), DARK_METAL, "rezzo")
s.mesh_node("stabilizer_rear", cylinder((XR - 0.15, 0.28, -0.56), (XR - 0.15, 0.28, 0.56), 0.011), DARK_METAL, "rezzo")

# ---------------------------------------------------------------- силовой агрегат (условное размещение)
s.mesh_node("engine_block", box(1.28, 1.62, 0.32, 0.70, -0.42, 0.12), CAST, "rezzo")
s.mesh_node("cylinder_head", box(1.27, 1.63, 0.70, 0.79, -0.42, 0.12), ALU, "rezzo")
s.mesh_node("valve_cover", box(1.32, 1.58, 0.79, 0.835, -0.40, 0.10), DARK_METAL, "rezzo")
s.mesh_node("ignition_coil", box(1.41, 1.49, 0.835, 0.865, -0.36, 0.06), BLACK_PLASTIC, "rezzo")
plugs = Mesh()
for zc in (-0.315, -0.185, -0.055, 0.075):
    plugs.merge(cylinder((1.45, 0.865, zc), (1.45, 0.89, zc), 0.011, seg=10))
s.mesh_node("spark_plugs", plugs, BLACK_PLASTIC, "rezzo")
s.mesh_node("oil_pan", box(1.32, 1.60, 0.22, 0.32, -0.40, 0.08), DARK_METAL, "rezzo")
s.mesh_node("timing_cover", box(1.30, 1.60, 0.38, 0.84, -0.47, -0.42), BLACK_PLASTIC, "rezzo")
s.mesh_node("intake_manifold", box(1.17, 1.28, 0.56, 0.80, -0.38, 0.08), BLACK_PLASTIC, "rezzo")
s.mesh_node("throttle_body", cylinder((1.21, 0.70, 0.08), (1.21, 0.70, 0.16), 0.035), ALU, "rezzo")
s.mesh_node("fuel_rail", cylinder((1.265, 0.77, -0.38), (1.265, 0.77, 0.08), 0.012), ALU, "rezzo")
inj = Mesh()
for zc in (-0.315, -0.185, -0.055, 0.075):
    inj.merge(cylinder((1.265, 0.76, zc), (1.285, 0.68, zc), 0.009, seg=8))
s.mesh_node("injectors", inj, BLACK_PLASTIC, "rezzo")
s.mesh_node("exhaust_manifold", box(1.62, 1.70, 0.48, 0.74, -0.38, 0.08), EXHAUST, "rezzo")
s.mesh_node("crank_pulley", cylinder((1.42, 0.38, -0.50), (1.42, 0.38, -0.47), 0.075), DARK_METAL, "rezzo")
s.mesh_node("alternator", cylinder((1.66, 0.62, -0.50), (1.66, 0.62, -0.37), 0.065), ALU, "rezzo")
s.mesh_node("accessory_belt", extrude_xy(strip((1.42, 0.38), (1.66, 0.62), 0.17), -0.512, -0.502), RUBBER, "rezzo")
s.mesh_node("water_pump", cylinder((1.50, 0.52, -0.50), (1.50, 0.52, -0.42), 0.045), ALU, "rezzo")
s.mesh_node("starter", cylinder((1.22, 0.36, 0.10), (1.22, 0.36, 0.28), 0.048), DARK_METAL, "rezzo")
s.mesh_node("gearbox", box(1.18, 1.52, 0.26, 0.62, 0.16, 0.52), ALU, "rezzo")
s.mesh_node("clutch_housing", cylinder((1.38, 0.44, 0.12), (1.38, 0.44, 0.19), 0.18, seg=28), ALU, "rezzo")
s.mesh_node("air_filter", box(1.66, 1.92, 0.71, 0.82, 0.05, 0.42), FILTER_BOX, "rezzo")
s.mesh_node("air_duct", cylinder((1.66, 0.79, 0.20), (1.24, 0.71, 0.16), 0.04), HOSE, "rezzo")
s.mesh_node("battery", box(1.64, 1.88, 0.58, 0.78, 0.47, 0.74), BATTERY, "rezzo")
s.mesh_node("fuse_box", box(1.70, 1.82, 0.74, 0.83, -0.72, -0.56), BLACK_PLASTIC, "rezzo")
s.mesh_node("brake_booster", cylinder((1.02, 0.80, 0.40), (1.10, 0.80, 0.40), 0.11, seg=28), DARK_METAL, "rezzo")
s.mesh_node("master_cylinder", cylinder((1.10, 0.80, 0.40), (1.24, 0.80, 0.40), 0.03), ALU, "rezzo")

# охлаждение
s.mesh_node("radiator", box(1.95, 2.00, 0.32, 0.74, -0.60, 0.60), RADIATOR, "rezzo")
s.mesh_node("cooling_fan", cylinder((1.89, 0.53, -0.12), (1.93, 0.53, -0.12), 0.17, seg=28), BLACK_PLASTIC, "rezzo")
s.mesh_node("coolant_reservoir", box(1.72, 1.86, 0.68, 0.80, -0.53, -0.40), RESERVOIR, "rezzo")
hoses = cylinder((1.66, 0.76, -0.10), (1.96, 0.70, -0.45), 0.022).merge(cylinder((1.55, 0.36, -0.42), (1.96, 0.34, 0.40), 0.022))
s.mesh_node("coolant_hoses", hoses, HOSE, "rezzo")
s.mesh_node("thermostat", cylinder((1.62, 0.76, 0.10), (1.66, 0.76, 0.10), 0.03), ALU, "rezzo")

# выпуск, топливо
s.mesh_node("exhaust_downpipe", cylinder((1.68, 0.48, -0.15), (1.45, 0.22, -0.08), 0.03).merge(
    cylinder((1.45, 0.22, -0.08), (0.95, 0.20, -0.02), 0.03)), EXHAUST, "rezzo")
s.mesh_node("catalytic_converter", box(0.55, 0.95, 0.15, 0.25, -0.12, 0.10), EXHAUST, "rezzo")
s.mesh_node("exhaust_pipe", cylinder((0.55, 0.20, -0.02), (-0.30, 0.20, -0.32), 0.026).merge(
    cylinder((-0.30, 0.20, -0.32), (-1.55, 0.21, -0.35), 0.026)), EXHAUST, "rezzo")
s.mesh_node("muffler", box(-2.02, -1.55, 0.16, 0.30, -0.55, -0.15), EXHAUST, "rezzo")
s.mesh_node("tailpipe", cylinder((-2.02, 0.22, -0.45), (-2.23, 0.22, -0.45), 0.024), EXHAUST, "rezzo")
s.mesh_node("fuel_tank", box(-1.15, -0.50, 0.15, 0.24, -0.12, 0.66), FUEL_TANK, "rezzo")
s.mesh_node("fuel_pump", cylinder((-0.80, 0.24, 0.30), (-0.80, 0.30, 0.30), 0.045), BLACK_PLASTIC, "rezzo")
s.mesh_node("fuel_line", cylinder((-0.50, 0.21, 0.60), (0.92, 0.21, 0.60), 0.007).merge(
    cylinder((0.92, 0.21, 0.60), (1.265, 0.77, 0.08), 0.007)), ALU, "rezzo")

# ---------------------------------------------------------------- салон
s.mesh_node("dashboard", extrude_xy([(0.98, 0.60), (0.98, 0.98), (0.80, 1.0), (0.56, 0.93), (0.56, 0.80), (0.76, 0.62)], -0.80, 0.80), INTERIOR, "rezzo")
s.mesh_node("instrument_cluster", box(0.60, 0.70, 0.93, 1.01, 0.22, 0.52), BLACK_PLASTIC, "rezzo")
SW_C = (0.47, 0.98, 0.37)
SW_AX = (-math.cos(math.radians(62)), math.sin(math.radians(62)), 0.0)
s.mesh_node("steering_wheel", torus(SW_C, SW_AX, 0.185, 0.016).merge(
    cylinder(SW_C, (0.76, 0.82, 0.37), 0.025)), BLACK_PLASTIC, "rezzo")
s.mesh_node("gear_lever", cylinder((0.62, 0.62, 0.0), (0.52, 0.80, 0.0), 0.009).merge(
    cylinder((0.51, 0.79, 0.0), (0.505, 0.84, 0.0), 0.022)), BLACK_PLASTIC, "rezzo")
ped = Mesh()
for zc in (0.47, 0.38, 0.27):      # сцепление, тормоз, газ (слева направо)
    ped.merge(box(0.80, 0.83, 0.34, 0.44, zc - 0.035, zc + 0.035))
s.mesh_node("pedals", ped, BLACK_PLASTIC, "rezzo")
for sd, name in ((1, "seat_front_l"), (-1, "seat_front_r")):
    zc = sd * 0.37
    m = box(-0.25, 0.25, 0.42, 0.55, zc - 0.24, zc + 0.24)
    m.merge(extrude_xy([(-0.30, 0.53), (-0.20, 0.53), (-0.30, 1.20), (-0.40, 1.20)], zc - 0.23, zc + 0.23))
    m.merge(box(-0.43, -0.33, 1.20, 1.36, zc - 0.13, zc + 0.13))
    s.mesh_node(name, m, SEAT, "rezzo")
rs = box(-1.25, -0.75, 0.42, 0.56, -0.70, 0.70)
rs.merge(extrude_xy([(-1.32, 0.55), (-1.22, 0.55), (-1.32, 1.18), (-1.42, 1.18)], -0.70, 0.70))
s.mesh_node("seat_rear", rs, SEAT, "rezzo")

# земля (не деталь машины; скрывается в режиме «вид снизу»)
s.mesh_node("ground", cylinder((0, -0.012, 0), (0, -0.002, 0), 3.4, seg=64), GROUND, None)

size = s.write_glb(OUT_GLB)

# ---------------------------------------------------------------- конфигурация сцены
def b(node, part, side=None):
    d = {"node": node, "partId": part}
    if side:
        d["side"] = side
    return d


SIDE_RU = {"l": "левая", "r": "правая"}
SIDE_RU_M = {"l": "левый", "r": "правый"}
SIDE_RU_N = {"l": "левое", "r": "правое"}
parts = []
for sfx in ("l", "r"):
    parts += [
        b(f"door_front_{sfx}", "door_front", SIDE_RU[sfx]),
        b(f"door_rear_{sfx}", "door_rear", SIDE_RU[sfx]),
        b(f"window_front_{sfx}", "side_window", "передняя " + SIDE_RU[sfx]),
        b(f"window_rear_{sfx}", "side_window", "задняя " + SIDE_RU[sfx]),
        b(f"headlight_{sfx}", "headlight", SIDE_RU[sfx]),
        b(f"turn_front_{sfx}", "turn_signal", "передний " + SIDE_RU_M[sfx]),
        b(f"turn_rear_{sfx}", "turn_signal", "задний " + SIDE_RU_M[sfx]),
        b(f"taillight_{sfx}", "taillight", SIDE_RU_M[sfx]),
        b(f"reverse_light_{sfx}", "reverse_light", SIDE_RU_M[sfx]),
        b(f"strut_front_{sfx}", "strut_front", SIDE_RU[sfx]),
        b(f"spring_front_{sfx}", "coil_spring", "передняя " + SIDE_RU[sfx]),
        b(f"lower_arm_front_{sfx}", "lower_arm", SIDE_RU_M[sfx]),
        b(f"tie_rod_{sfx}", "tie_rod", SIDE_RU[sfx]),
        b(f"driveshaft_{sfx}", "driveshaft", SIDE_RU[sfx]),
        b(f"shock_rear_{sfx}", "shock_rear", SIDE_RU_M[sfx]),
        b(f"spring_rear_{sfx}", "coil_spring", "задняя " + SIDE_RU[sfx]),
        b(f"links_rear_{sfx}", "rear_suspension", SIDE_RU[sfx]),
        b(f"brake_disc_f{sfx}", "brake_disc", SIDE_RU_M[sfx]),
        b(f"caliper_f{sfx}", "brake_caliper", SIDE_RU_M[sfx]),
        b(f"brake_drum_r{sfx}", "brake_drum", SIDE_RU_M[sfx]),
        b(f"wheel_f{sfx}", "wheel", "переднее " + SIDE_RU_N[sfx]),
        b(f"wheel_r{sfx}", "wheel", "заднее " + SIDE_RU_N[sfx]),
        b(f"seat_front_{sfx}", "seats", "водителя" if sfx == "l" else "пассажира"),
    ]
for n in body_nodes:
    if n.startswith(("fender", "pillar", "quarter", "roof", "sill", "rear_panel", "cowl", "front_panel")):
        parts.append(b(n, "body_shell"))
parts += [b("bumper_front", "bumper", "передний"), b("bumper_rear", "bumper", "задний")]
for n in ("glass_quarter_front_l", "glass_quarter_front_r", "glass_quarter_rear_l", "glass_quarter_rear_r", "tailgate_glass"):
    parts.append(b(n, "fixed_glass"))
parts += [b(n, n) for n in (
    "hood", "tailgate", "windshield",
    "engine_block", "cylinder_head", "valve_cover", "ignition_coil", "spark_plugs",
    "intake_manifold", "throttle_body", "fuel_rail", "injectors", "exhaust_manifold",
    "alternator", "accessory_belt", "water_pump", "starter", "gearbox",
    "air_filter", "battery", "fuse_box", "brake_booster", "master_cylinder",
    "radiator", "cooling_fan", "coolant_reservoir", "coolant_hoses", "thermostat",
    "catalytic_converter", "exhaust_pipe", "muffler",
    "fuel_tank", "fuel_pump", "fuel_line", "steering_rack",
    "dashboard", "instrument_cluster", "steering_wheel", "gear_lever", "pedals")]
parts += [b("floor", "body_shell", "пол"), b("firewall", "body_shell", "моторный щит"),
          b("oil_pan", "oil_system", "масляный картер"), b("timing_cover", "timing_drive", "кожух ГРМ"),
          b("crank_pulley", "crankshaft", "шкив коленвала"), b("clutch_housing", "clutch", "картер сцепления"),
          b("air_duct", "air_filter", "воздуховод"), b("exhaust_downpipe", "exhaust_pipe", "приёмная труба"),
          b("tailpipe", "exhaust_pipe", "выпускная труба"), b("stabilizer_front", "stabilizer", "передний"),
          b("stabilizer_rear", "stabilizer", "задний")]
parts.append(b("seat_rear", "seats", "задний диван"))

movables = []
for pivot, win, drop in doors:
    side = 1 if pivot.endswith("_l") else -1
    movables.append({"id": pivot, "node": pivot, "type": "rotate", "axis": [0, 1, 0], "angle": 65 * side, "group": "doors", "duration": 0.9})
    movables.append({"id": win, "node": win, "type": "translate", "offset": [0, -drop, 0], "group": "windows", "duration": 1.6})
movables.append({"id": "hood", "node": "hood", "type": "rotate", "axis": [0, 0, 1], "angle": 52, "group": "hood", "duration": 1.1})
movables.append({"id": "tailgate", "node": "tailgate", "type": "rotate", "axis": [0, 0, 1], "angle": -82, "group": "tailgate", "duration": 1.3})

light_defs = [
    {"id": "parking", "nodes": lights["headlight"] + lights["taillight"], "color": [1.0, 0.25, 0.18], "intensity": 0.35,
     "perNodeColor": {n: [0.9, 0.9, 0.85] for n in lights["headlight"]}},
    {"id": "headlights", "nodes": lights["headlight"] + lights["taillight"], "color": [1.0, 0.25, 0.18], "intensity": 1.0,
     "perNodeColor": {**{n: [1.0, 0.96, 0.86] for n in lights["headlight"]}, **{n: [0.6, 0.06, 0.04] for n in lights["taillight"]}}},
    {"id": "turn_left", "nodes": ["turn_front_l", "turn_rear_l"], "color": [1.0, 0.55, 0.05], "intensity": 1.2, "blink": True},
    {"id": "turn_right", "nodes": ["turn_front_r", "turn_rear_r"], "color": [1.0, 0.55, 0.05], "intensity": 1.2, "blink": True},
    {"id": "brake", "nodes": lights["taillight"], "color": [1.0, 0.04, 0.03], "intensity": 1.4},
    {"id": "reverse", "nodes": lights["reverse"], "color": [1.0, 1.0, 0.95], "intensity": 1.0},
]

all_body = body_nodes + [f"{p}_panel" for p, _, _ in doors] + [f"{p}_frame" for p, _, _ in doors] + \
    ["hood_panel", "tailgate_panel", "tailgate_frame"] + [f"{p}_handle" for p, _, _ in doors]
glass_all = glass_nodes + [w for _, w, _ in doors]
section_hidden = [n for n in all_body if n.endswith("_r") or "_r_" in n] + \
    ["roof", "hood", "windshield", "glass_quarter_front_r", "glass_quarter_rear_r", "window_front_r", "window_rear_r"]

disassembly = [
    {"title": "Капот", "nodes": ["hood"], "offset": [0.4, 1.4, 0]},
    {"title": "Дверь багажника", "nodes": ["tailgate"], "offset": [-1.2, 0.8, 0]},
    {"title": "Передние двери", "nodes": ["door_front_l", "door_front_r"], "offset": [0, 0, 1.4], "mirrorZ": True},
    {"title": "Задние двери", "nodes": ["door_rear_l", "door_rear_r"], "offset": [0, 0, 1.4], "mirrorZ": True},
    {"title": "Крыша и лобовое стекло", "nodes": ["roof", "windshield", "roof_rail_l", "roof_rail_r"], "offset": [0, 1.6, 0]},
    {"title": "Боковины кузова", "nodes": [n for n in body_nodes if n.startswith(("fender", "pillar", "quarter", "glass_quarter", "sill"))], "offset": [0, 0, 1.2], "mirrorZ": True},
    {"title": "Бамперы и облицовка", "nodes": ["bumper_front", "bumper_rear", "front_panel", "rear_panel", "cowl"] + lights["headlight"] + lights["turn_front"] + lights["taillight"] + lights["turn_rear"] + lights["reverse"], "offset": [0, 0.9, 0]},
    {"title": "Сиденья и салон", "nodes": ["seat_front_l", "seat_front_r", "seat_rear", "dashboard", "instrument_cluster", "steering_wheel", "gear_lever", "pedals"], "offset": [0, 1.5, 0]},
    {"title": "Колёса", "nodes": wheels, "offset": [0, 0, 0.9], "mirrorZ": True},
    {"title": "Впуск и воздушный фильтр", "nodes": ["air_filter", "air_duct", "intake_manifold", "throttle_body"], "offset": [0, 0.9, 0]},
    {"title": "Клапанная крышка, катушка и свечи", "nodes": ["valve_cover", "ignition_coil", "spark_plugs", "fuel_rail", "injectors"], "offset": [0, 0.8, 0]},
    {"title": "Аккумулятор и блок предохранителей", "nodes": ["battery", "fuse_box"], "offset": [0, 0.8, 0]},
    {"title": "Радиатор и вентилятор", "nodes": ["radiator", "cooling_fan", "coolant_reservoir", "coolant_hoses"], "offset": [0.9, 0.3, 0]},
]

def _is_right(n):
    return n.endswith("_r") or n in ("wheel_fr", "wheel_rr")


disassembly = [
    {"title": st["title"],
     "nodes": [{"node": n, "offset": [st["offset"][0], st["offset"][1],
                                      -st["offset"][2] if (st.get("mirrorZ") and _is_right(n)) else st["offset"][2]]}
               for n in st["nodes"]]}
    for st in disassembly
]

hotspots = [
    {"partId": "engine_block", "node": "engine_block", "pos": [1.45, 0.95, -0.15]},
    {"partId": "gearbox", "node": "gearbox", "pos": [1.35, 0.66, 0.36]},
    {"partId": "battery", "node": "battery", "pos": [1.76, 0.82, 0.60]},
    {"partId": "radiator", "node": "radiator", "pos": [2.02, 0.70, 0.30]},
    {"partId": "air_filter", "node": "air_filter", "pos": [1.79, 0.88, 0.24]},
    {"partId": "hood", "node": "hood_panel", "pos": [1.60, 0.90, 0.0]},
    {"partId": "door_front", "node": "door_front_l_panel", "pos": [0.40, 0.70, 0.89]},
    {"partId": "tailgate", "node": "tailgate_panel", "pos": [-2.14, 0.80, 0.0]},
    {"partId": "headlight", "node": "headlight_l", "pos": [2.15, 0.725, 0.64]},
    {"partId": "taillight", "node": "taillight_l", "pos": [-2.17, 1.04, 0.79]},
    {"partId": "strut_front", "node": "strut_front_l", "pos": [1.22, 0.86, 0.62]},
    {"partId": "brake_disc", "node": "brake_disc_fl", "pos": [1.25, 0.307, 0.80]},
    {"partId": "rear_suspension", "node": "links_rear_l", "pos": [-1.35, 0.30, 0.45]},
    {"partId": "muffler", "node": "muffler", "pos": [-1.78, 0.18, -0.35]},
    {"partId": "fuel_tank", "node": "fuel_tank", "pos": [-0.82, 0.18, 0.30]},
    {"partId": "steering_wheel", "node": "steering_wheel", "pos": [0.47, 1.12, 0.37]},
    {"partId": "dashboard", "node": "instrument_cluster", "pos": [0.65, 1.03, 0.37]},
    {"partId": "seats", "node": "seat_front_r", "pos": [-0.1, 0.80, -0.37]},
]

config = {
    "comment": "Конфигурация сцены. Узлы (node) — имена из GLB. Сгенерировано tools/gen_placeholder_car.py",
    "model": "models/rezzo_placeholder.glb",
    "placeholder": True,
    "placeholderNote": "Учебная заглушка: габариты 4350×1755×1580 мм и база 2600 мм — по паспортным данным, форма кузова и размещение агрегатов — условные.",
    "home": {"target": [0.0, 0.65, 0.0], "yaw": 38, "pitch": 16, "distance": 7.2},
    "interior": {"eye": [-0.05, 1.20, 0.37], "yaw": 0, "pitch": -10},
    "parts": parts,
    "movables": movables,
    "lights": light_defs,
    "groups": {
        "body": all_body,
        "glass": glass_all,
        "section_hidden": section_hidden,
        "ground": ["ground"],
    },
    "disassembly": disassembly,
    "hotspots": hotspots,
}
os.makedirs(os.path.dirname(OUT_CFG), exist_ok=True)
with open(OUT_CFG, "w", encoding="utf-8") as f:
    json.dump(config, f, ensure_ascii=False, indent=1)

print(f"GLB: {OUT_GLB} ({size} байт), узлов: {len(s.nodes)}, мешей: {len(s.meshes)}")
print(f"Конфиг: {OUT_CFG}, привязок: {len(parts)}, подвижных: {len(movables)}")
