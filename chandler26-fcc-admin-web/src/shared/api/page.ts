export interface PageResult<T> {
  pageNum: number;
  pageSize: number;
  total: number;
  list: T[];
}
