#!/usr/bin/env python3
"""
Конвертер SVG → VectorDrawable под конкретный набор иконок KidGuard.

Готовые инструменты не подошли: svg2vectordrawable молча выбрасывает <circle>/<ellipse>
и неправильно пересчитывает координаты градиентов из objectBoundingBox. Здесь поддержан
ровно тот набор примитивов, который используется в макете, зато точно и предсказуемо.

Ключевые моменты преобразования:
- circle/ellipse/rect разворачиваются в pathData дугами (VectorDrawable не знает этих тегов);
- градиенты в SVG заданы в долях bbox фигуры (objectBoundingBox по умолчанию), а VectorDrawable
  хочет абсолютные координаты viewport — пересчитываем по bbox каждой фигуры;
- transform="rotate(a cx cy)" превращается в <group android:rotation/pivotX/pivotY>.
"""
import re
import sys
import pathlib
import xml.etree.ElementTree as ET

SVG_NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', SVG_NS)


def num(v, default=0.0):
    return float(v) if v not in (None, '') else default


def fmt(v):
    """Короткая запись числа: 48.0 -> 48, 1.50 -> 1.5."""
    s = f'{v:.3f}'.rstrip('0').rstrip('.')
    return s if s not in ('', '-0') else '0'


def circle_path(cx, cy, r):
    return ellipse_path(cx, cy, r, r)


def ellipse_path(cx, cy, rx, ry):
    x0 = cx - rx
    return (f'M{fmt(x0)},{fmt(cy)} '
            f'a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0 '
            f'a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0 Z')


def rect_path(x, y, w, h, rx, ry):
    if rx <= 0 and ry <= 0:
        return f'M{fmt(x)},{fmt(y)} h{fmt(w)} v{fmt(h)} h{fmt(-w)} Z'
    rx = min(rx or ry, w / 2)
    ry = min(ry or rx, h / 2)
    return (f'M{fmt(x + rx)},{fmt(y)} '
            f'h{fmt(w - 2 * rx)} a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(ry)} '
            f'v{fmt(h - 2 * ry)} a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(ry)} '
            f'h{fmt(-(w - 2 * rx))} a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(-ry)} '
            f'v{fmt(-(h - 2 * ry))} a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(-ry)} Z')


# bbox для path-фигур с градиентом задаём вручную: считать его по кривым ради двух случаев
# не стоит. Ключ — начало атрибута d.
MANUAL_BBOX = {
    'M30 44 V32 a18 18 0 0 1 36 0 V44': (25, 9, 46, 40),   # дужка замка со stroke-width 10
}


def bbox_of(el):
    tag = el.tag.split('}')[-1]
    if tag == 'circle':
        cx, cy, r = num(el.get('cx')), num(el.get('cy')), num(el.get('r'))
        return (cx - r, cy - r, 2 * r, 2 * r)
    if tag == 'ellipse':
        cx, cy = num(el.get('cx')), num(el.get('cy'))
        rx, ry = num(el.get('rx')), num(el.get('ry'))
        return (cx - rx, cy - ry, 2 * rx, 2 * ry)
    if tag == 'rect':
        return (num(el.get('x')), num(el.get('y')), num(el.get('width')), num(el.get('height')))
    if tag == 'path':
        d = ' '.join(el.get('d', '').split())
        for key, box in MANUAL_BBOX.items():
            if d.startswith(key):
                return box
        raise SystemExit(f'Нет bbox для path: {d[:60]}… — добавь в MANUAL_BBOX')
    raise SystemExit(f'Неизвестный тег: {tag}')


def to_argb(color, alpha=1.0):
    color = color.strip()
    if color.startswith('#'):
        h = color[1:]
        if len(h) == 3:
            h = ''.join(c * 2 for c in h)
        a = round(alpha * 255)
        return f'#{a:02X}{h.upper()}'
    if color == 'none':
        return '#00000000'
    raise SystemExit(f'Неподдерживаемый цвет: {color}')


def parse_gradients(root):
    """id -> (координаты в долях, [(offset, color), ...])"""
    out = {}
    for g in root.iter(f'{{{SVG_NS}}}linearGradient'):
        stops = []
        for s in g:
            stops.append((s.get('offset', '0'), s.get('stop-color')))
        out[g.get('id')] = (
            (num(g.get('x1')), num(g.get('y1')), num(g.get('x2'), 1.0), num(g.get('y2'))),
            stops,
        )
    return out


def gradient_xml(grad, box, indent):
    (x1, y1, x2, y2), stops = grad
    bx, by, bw, bh = box
    pad = ' ' * indent
    lines = [f'{pad}<aapt:attr name="android:fillColor">',
             f'{pad}    <gradient',
             f'{pad}        android:type="linear"',
             f'{pad}        android:startX="{fmt(bx + x1 * bw)}"',
             f'{pad}        android:startY="{fmt(by + y1 * bh)}"',
             f'{pad}        android:endX="{fmt(bx + x2 * bw)}"',
             f'{pad}        android:endY="{fmt(by + y2 * bh)}">']
    for offset, color in stops:
        lines.append(f'{pad}        <item android:offset="{offset}" '
                     f'android:color="{to_argb(color)}"/>')
    lines.append(f'{pad}    </gradient>')
    lines.append(f'{pad}</aapt:attr>')
    return '\n'.join(lines)


ROTATE_RE = re.compile(r'rotate\(\s*(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)\s*\)')
TRANSLATE_RE = re.compile(r'translate\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*\)')


def shape_to_path_data(el):
    tag = el.tag.split('}')[-1]
    tx = ty = 0.0
    m = TRANSLATE_RE.search(el.get('transform', ''))
    if m:
        # Единственный случай в наборе — тень перечёркивания глобуса. Смещаем координаты
        # сразу, чтобы не городить вложенную группу ради двух пикселей.
        tx, ty = float(m.group(1)), float(m.group(2))
    if tag == 'circle':
        return circle_path(num(el.get('cx')) + tx, num(el.get('cy')) + ty, num(el.get('r')))
    if tag == 'ellipse':
        return ellipse_path(num(el.get('cx')) + tx, num(el.get('cy')) + ty,
                            num(el.get('rx')), num(el.get('ry')))
    if tag == 'rect':
        return rect_path(num(el.get('x')) + tx, num(el.get('y')) + ty,
                         num(el.get('width')), num(el.get('height')),
                         num(el.get('rx')), num(el.get('ry')))
    if tag == 'path':
        return ' '.join(el.get('d').split())
    raise SystemExit(f'Неизвестный тег фигуры: {tag}')


def convert(src, dst, header):
    tree = ET.parse(src)
    root = tree.getroot()
    grads = parse_gradients(root)

    out = [header,
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="24dp"',
           '    android:height="24dp"',
           '    android:viewportWidth="96"',
           '    android:viewportHeight="96">',
           '']

    def emit(el, inherited, indent):
        tag = el.tag.split('}')[-1]
        if tag in ('defs', 'linearGradient', 'stop'):
            return
        attrs = dict(inherited)
        for k in ('fill', 'stroke', 'opacity', 'fill-opacity', 'stroke-opacity',
                  'stroke-width', 'stroke-linecap', 'stroke-linejoin'):
            if el.get(k) is not None:
                attrs[k] = el.get(k)
        if tag == 'g':
            for child in el:
                emit(child, attrs, indent)
            return

        pad = ' ' * indent
        rot = ROTATE_RE.search(el.get('transform', ''))
        if rot:
            angle, px, py = rot.groups()
            out.append(f'{pad}<group android:rotation="{angle}" '
                       f'android:pivotX="{px}" android:pivotY="{py}">')
            indent += 4
            pad = ' ' * indent

        data = shape_to_path_data(el)
        lines = [f'{pad}<path']
        lines.append(f'{pad}    android:pathData="{data}"')

        fill = attrs.get('fill', '#000000')
        alpha = float(attrs.get('opacity', 1.0)) * float(attrs.get('fill-opacity', 1.0))
        grad_ref = None
        if fill.startswith('url(#'):
            grad_ref = fill[5:-1]
        elif fill != 'none':
            lines.append(f'{pad}    android:fillColor="{to_argb(fill)}"')
            if alpha < 1:
                lines.append(f'{pad}    android:fillAlpha="{fmt(alpha)}"')
        else:
            lines.append(f'{pad}    android:fillColor="#00000000"')

        stroke = attrs.get('stroke')
        stroke_grad = None
        if stroke and stroke != 'none':
            if stroke.startswith('url(#'):
                stroke_grad = stroke[5:-1]
            else:
                lines.append(f'{pad}    android:strokeColor="{to_argb(stroke)}"')
            sa = float(attrs.get('opacity', 1.0)) * float(attrs.get('stroke-opacity', 1.0))
            if sa < 1:
                lines.append(f'{pad}    android:strokeAlpha="{fmt(sa)}"')
            if attrs.get('stroke-width'):
                lines.append(f'{pad}    android:strokeWidth="{attrs["stroke-width"]}"')
            if attrs.get('stroke-linecap'):
                lines.append(f'{pad}    android:strokeLineCap="{attrs["stroke-linecap"]}"')
            if attrs.get('stroke-linejoin'):
                lines.append(f'{pad}    android:strokeLineJoin="{attrs["stroke-linejoin"]}"')

        if grad_ref or stroke_grad:
            lines[-1] = lines[-1] + '>'
            out.extend(lines)
            ref = grad_ref or stroke_grad
            name = 'android:fillColor' if grad_ref else 'android:strokeColor'
            block = gradient_xml(grads[ref], bbox_of(el), indent + 4)
            out.append(block.replace('android:fillColor', name, 1))
            out.append(f'{pad}</path>')
        else:
            lines[-1] = lines[-1] + ' />'
            out.extend(lines)

        if rot:
            indent -= 4
            out.append(f'{" " * indent}</group>')

    for child in root:
        emit(child, {}, 4)

    out.append('</vector>')
    pathlib.Path(dst).write_text('\n'.join(out) + '\n', encoding='utf-8')


if __name__ == '__main__':
    convert(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else '')
