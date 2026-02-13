import matplotlib.pyplot as plt
import csv

# Read throughput data
time_seconds = []
throughput = []

with open('throughput.csv', 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        time_seconds.append(int(row['time_seconds']))
        throughput.append(float(row['throughput_per_second']))

# Create figure
fig, ax = plt.subplots(figsize=(10, 6))

# Plot line chart
ax.plot(time_seconds, throughput, 'b-o', linewidth=2, markersize=8, label='Throughput')

# Fill area under curve
ax.fill_between(time_seconds, throughput, alpha=0.15, color='blue')

# Labels and title
ax.set_xlabel('Time (seconds)', fontsize=12)
ax.set_ylabel('Throughput (messages/second)', fontsize=12)
ax.set_title('ChatFlow Load Test - Throughput Over Time (10s buckets)', fontsize=14, fontweight='bold')

# Format y-axis with comma separator
ax.get_yaxis().set_major_formatter(plt.FuncFormatter(lambda x, p: f'{x:,.0f}'))

# Add grid
ax.grid(True, alpha=0.3)

# Annotate phases
ax.annotate('Warmup\n(32 threads)', xy=(0, throughput[0]),
            xytext=(3, throughput[0] + 4000),
            fontsize=9, ha='center', color='gray',
            arrowprops=dict(arrowstyle='->', color='gray'))

peak_idx = throughput.index(max(throughput))
ax.annotate(f'Peak: {max(throughput):,.0f} msg/s\n(512 threads)',
            xy=(time_seconds[peak_idx], max(throughput)),
            xytext=(time_seconds[peak_idx] - 5, max(throughput) + 2000),
            fontsize=9, ha='center', color='darkblue',
            arrowprops=dict(arrowstyle='->', color='darkblue'))

# Add legend
ax.legend(fontsize=11)

plt.tight_layout()
plt.savefig('throughput_chart.png', dpi=150)
plt.savefig('throughput_chart.pdf')
print('Charts saved: throughput_chart.png, throughput_chart.pdf')
