-- $ID$
-- TPC-H/TPC-R Shipping Priority Query (Q3)
-- Functional Query Definition
-- Approved February 1998

SET SESSION accumulo.optimize_column_filters_enabled = false;

select
	l.orderkey,
	sum(l.extendedprice * (1 - l.discount)) as revenue,
	o.orderdate,
	o.shippriority
from
	${SCHEMA}.customer c,
	${SCHEMA}.orders o,
	${SCHEMA}.lineitem l
where
	c.mktsegment = 'BUILDING'
	and c.custkey = o.custkey
	and l.orderkey = o.orderkey
	and o.orderdate < date '1995-03-15'
	and l.shipdate > date '1995-03-15'
group by
	l.orderkey,
	o.orderdate,
	o.shippriority
order by
	revenue desc,
	o.orderdate;
