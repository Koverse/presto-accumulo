-- $ID$
-- TPC-H/TPC-R Returned Item Reporting Query (Q10)
-- Functional Query Definition
-- Approved February 1998

SET SESSION accumulo.optimize_column_filters_enabled = false;

select
	c.custkey,
	c.name,
	sum(l.extendedprice * (1 - l.discount)) as revenue,
	c.acctbal,
	n.name,
	c.address,
	c.phone,
	c.comment
from
	${SCHEMA}.customer c,
	${SCHEMA}.orders o,
	${SCHEMA}.lineitem l,
	${SCHEMA}.nation n
where
	c.custkey = o.custkey
	and l.orderkey = o.orderkey
	and o.orderdate >= date '1993-10-01'
	and o.orderdate < date '1993-10-01' + interval '3' month
	and l.returnflag = 'R'
	and c.nationkey = n.nationkey
group by
	c.custkey,
	c.name,
	c.acctbal,
	c.phone,
	n.name,
	c.address,
	c.comment
order by
	revenue desc;
