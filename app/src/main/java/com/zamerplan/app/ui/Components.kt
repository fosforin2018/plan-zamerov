@Composable
fun ZamerCard(
    z: Zamer,
    hasVoice: Boolean,
    onCall: () -> Unit,
    onDone: () -> Unit,
    onReturn: () -> Unit,
    onReschedule: () -> Unit,
    onEdit: () -> Unit,
    onMap: () -> Unit,
    onPlayVoice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(4.dp).shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        border = BorderStroke(1.dp, DarkCardBorder)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(6.dp).background(statusColor(z.status)))
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasVoice) {
                        Box(
                            modifier = Modifier.size(28.dp).background(Orange.copy(alpha = 0.3f), CircleShape).clickable { onPlayVoice() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎤", fontSize = 14.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Surface(color = statusColor(z.status), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            z.status.label,
                            color = Color.White,
                            fontSize = 9.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Время (фиксированная высота)
                Text(
                    z.timeText(),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Orange,
                    maxLines = 1,
                    lineHeight = 20.sp,
                    modifier = Modifier.height(24.dp)
                )

                // Имя (фиксированная высота)
                Text(
                    z.name.ifBlank { " " },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary,
                    modifier = Modifier.height(20.dp)
                )

                // Адрес (фиксированная высота 2 строки)
                Text(
                    z.address.ifBlank { " " },
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = TextSecondary,
                    modifier = Modifier.height(32.dp)
                )

                // Метаданные (фиксированная высота)
                val meta = listOf(
                    if (z.contactFrom.isNotBlank()) "От: " + z.contactFrom else "",
                    z.area,
                    z.thickness
                ).filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    meta.ifBlank { " " },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextSecondary,
                    modifier = Modifier.height(16.dp)
                )

                // Цена (фиксированная высота)
                Text(
                    (z.price + " ₽").ifBlank { " " },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    color = TextPrimary,
                    modifier = Modifier.height(18.dp)
                )

                // Комментарий (фиксированная высота)
                Text(
                    z.comment.ifBlank { " " },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextSecondary,
                    modifier = Modifier.height(16.dp)
                )

                // Первая строка кнопок
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    ActionButtonIcon(icon = Icons.Filled.Call, containerColor = Green, onClick = onCall)
                    ActionButtonIcon(icon = Icons.Filled.Place, containerColor = Blue, onClick = onMap)
                    ActionButtonIcon(icon = Icons.Filled.DateRange, containerColor = Orange, onClick = onReschedule)
                }

                // Вторая строка кнопок
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    if (z.status == ZamerStatus.DONE || z.status == ZamerStatus.CANCELLED) {
                        ActionButtonIcon(icon = Icons.Filled.Refresh, containerColor = Green, onClick = onReturn)
                    } else {
                        ActionButtonIcon(icon = Icons.Filled.CheckCircle, containerColor = Green, onClick = onDone)
                    }
                    ActionButtonIcon(icon = Icons.Filled.Edit, containerColor = Gray, onClick = onEdit)
                }
            }
        }
    }
}
