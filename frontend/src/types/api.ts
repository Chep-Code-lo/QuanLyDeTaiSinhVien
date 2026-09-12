export type ApiResponse<T> = {
  success: boolean
  message: string
  data: T
  timestamp: string
}

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
