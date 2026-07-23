/**
 * 根据布局树收集连线锚点对，供 DecisionTreeConnectorLayer 使用
 * @param {object|null} layout buildTreeLayout 结果
 * @returns {string[][]}
 */
export function collectLayoutSegments(layout) {
  const segments = []
  walk(layout, segments)
  return segments
}

/**
 * 递归遍历布局节点并写入 segments
 * @param {object|null} layout
 * @param {string[][]} segments
 */
function walk(layout, segments) {
  if (!layout) return
  if (layout.kind === 'cycle' || layout.kind === 'unknown') return

  if (layout.kind === 'start') {
    if (layout.child) {
      segments.push(['out-' + layout.id, 'in-' + layout.child.id])
      walk(layout.child, segments)
    }
    return
  }

  if (layout.kind === 'decision') {
    const branches = layout.branches || []
    for (let i = 0; i < branches.length; i++) {
      const br = branches[i]
      segments.push(['out-' + layout.id, 'edge-in-' + br.edgeId])
      if (br.child) {
        segments.push(['edge-out-' + br.edgeId, 'in-' + br.child.id])
        walk(br.child, segments)
      }
    }
    return
  }

  if (layout.kind === 'task') {
    if (layout.next) {
      segments.push(['out-' + layout.id, 'in-' + layout.next.id])
      walk(layout.next, segments)
    }
  }
}
