set terminal postscript eps color font 24
set key top left
set out "mapping_times_e3s_bb_without_routing_auto-indust-mocsyn.eps"
set title "Mapping times for E3S auto-indust-mocsyn CTGs,\nmade with Branch and Bound (BB), \nrun without routing, with default parameters"
set xlabel "CTG"
#set xrange [0:1]
#set xtics rotate by -90
set ylabel "Mapping time (ms)"
plot "mapping_times_e3s_sa_bb_without_routing_auto-indust-mocsyn.data" using 3:xticlabels(1) with boxes fs solid 0.7 title "BB"
